package edu.neusoft.newsplay;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class NewsController {
  private static final Logger log = LoggerFactory.getLogger(NewsController.class);
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final ExecutorService converter = Executors.newSingleThreadExecutor();
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  @Value("${newsplay.storage}") private String storageSetting;
  @Value("${newsplay.python}") private String python;
  @Value("${newsplay.initial-password:}") private String initialPassword;
  private Path storage;

  public NewsController(JdbcTemplate db, TransactionTemplate tx) { this.db = db; this.tx = tx; }

  @PostConstruct
  void init() throws IOException {
    storage = Path.of(storageSetting).toAbsolutePath().normalize();
    for (String folder : List.of("originals", "pages", "thumbnails", "assets")) Files.createDirectories(storage.resolve(folder));
    Integer count = db.queryForObject("SELECT COUNT(*) FROM admin_user", Integer.class);
    if (count != null && count == 0) {
      if (initialPassword.isBlank()) throw new IllegalStateException("首次运行须设置 NEWSPLAY_INITIAL_PASSWORD");
      db.update("INSERT INTO admin_user(username,password_hash) VALUES('admin',?)", encoder.encode(initialPassword));
      log.info("已创建初始管理员账号 username=admin");
    }
    int interrupted = db.update("UPDATE source_file SET convert_status='FAILED',error_message='服务重启中断了转换，请重新上传' WHERE convert_status='PROCESSING'");
    log.info("系统初始化完成 storage={} interruptedConversions={}", storage, interrupted);
  }

  private long admin(HttpSession session) {
    Object id = session.getAttribute("adminId");
    if (!(id instanceof Long)) throw new SecurityException("请先登录");
    return (Long) id;
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
    String username = body.getOrDefault("username", "");
    List<Map<String,Object>> rows = db.queryForList("SELECT id,username,password_hash FROM admin_user WHERE username=?", username);
    if (rows.isEmpty() || !encoder.matches(body.getOrDefault("password", ""), String.valueOf(rows.get(0).get("password_hash")))) {
      log.warn("管理员登录失败 username={}", username);
      throw new IllegalArgumentException("用户名或密码错误");
    }
    long adminId = ((Number)rows.get(0).get("id")).longValue();
    session.setAttribute("adminId", adminId);
    log.info("管理员登录成功 adminId={} username={}", adminId, username);
    return Map.of("username", rows.get(0).get("username"));
  }

  @GetMapping("/auth/me") public Map<String,Object> me(HttpSession session) { return Map.of("id", admin(session)); }
  @PostMapping("/auth/logout") public Map<String,Object> logout(HttpSession session) { Object adminId=session.getAttribute("adminId"); session.invalidate(); log.info("管理员退出 adminId={}", adminId); return Map.of("ok", true); }

  @PostMapping("/files/upload")
  public Map<String,Object> upload(@RequestParam MultipartFile file, HttpSession session) throws IOException {
    long user = admin(session);
    String name = file.getOriginalFilename() == null ? "upload.pdf" : file.getOriginalFilename();
    if (!name.toLowerCase().endsWith(".pdf") || file.isEmpty()) throw new IllegalArgumentException("仅支持非空 PDF 文件");
    byte[] header = file.getInputStream().readNBytes(5);
    if (!"%PDF-".equals(new String(header, java.nio.charset.StandardCharsets.US_ASCII))) throw new IllegalArgumentException("文件内容不是 PDF");
    String relative = "originals/" + UUID.randomUUID() + ".pdf";
    Files.copy(file.getInputStream(), storage.resolve(relative));
    GeneratedKeyHolder key = new GeneratedKeyHolder();
    db.update(connection -> {
      var statement = connection.prepareStatement("INSERT INTO source_file(original_name,file_path,convert_status,uploaded_by) VALUES(?,?,'PROCESSING',?)", java.sql.Statement.RETURN_GENERATED_KEYS);
      statement.setString(1,name); statement.setString(2,relative); statement.setLong(3,user);
      return statement;
    }, key);
    long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
    log.info("接收PDF上传 fileId={} adminId={} originalName={} size={}", id, user, name, file.getSize());
    converter.submit(() -> convert(id, relative));
    return Map.of("id", id, "status", "PROCESSING");
  }

  private void convert(long id, String relative) {
    log.info("开始转换PDF fileId={} source={}", id, relative);
    try {
      Path pages = storage.resolve("pages/" + id);
      Path thumbs = storage.resolve("thumbnails/" + id);
      Path script = Path.of("converter/convert.py").toAbsolutePath();
      Process process = new ProcessBuilder(python, script.toString(), storage.resolve(relative).toString(), pages.toString(), thumbs.toString())
          .redirectErrorStream(true).start();
      boolean finished = process.waitFor(Duration.ofMinutes(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!finished) { process.destroyForcibly(); throw new IOException("PDF 渲染超时"); }
      String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0) throw new IOException(output.isBlank() ? "PDF 处理失败" : output);
      int count = Integer.parseInt(output);
      tx.executeWithoutResult(status -> {
        for (int i = 1; i <= count; i++) {
          String page = String.format("pages/%d/page_%03d.webp", id, i);
          String thumb = String.format("thumbnails/%d/thumb_%03d.webp", id, i);
          db.update("INSERT INTO news_page(source_file_id,page_number,image_path,thumbnail_path) VALUES(?,?,?,?)", id, i, page, thumb);
        }
        db.update("UPDATE source_file SET page_count=?,convert_status='SUCCESS',error_message=NULL WHERE id=?", count, id);
      });
      log.info("PDF转换成功 fileId={} pages={}", id, count);
    } catch (Exception e) {
      db.update("UPDATE source_file SET convert_status='FAILED',error_message=? WHERE id=?", e.getMessage() == null ? "处理失败" : e.getMessage().substring(0, Math.min(1000,e.getMessage().length())), id);
      log.error("PDF转换失败 fileId={} source={}", id, relative, e);
    }
  }

  @GetMapping("/files")
  public List<Map<String,Object>> files(HttpSession session) { admin(session); return db.queryForList("SELECT id,original_name,page_count,convert_status,error_message,created_at FROM source_file ORDER BY id DESC"); }
  @GetMapping("/files/{id}")
  public Map<String,Object> file(@PathVariable long id, HttpSession session) { admin(session); return db.queryForMap("SELECT * FROM source_file WHERE id=?", id); }

  @GetMapping("/news")
  public List<Map<String,Object>> news(HttpSession session) { admin(session); return db.queryForList("SELECT n.id,n.source_file_id,n.page_number,n.play_status,n.sort_order,n.created_at,s.original_name,CONCAT('/api/media/',n.image_path) image_url,CONCAT('/api/media/',n.thumbnail_path) thumbnail_url FROM news_page n JOIN source_file s ON s.id=n.source_file_id ORDER BY s.id DESC,n.page_number"); }

  @GetMapping("/display/news")
  public List<Map<String,Object>> playing() { return db.queryForList("SELECT n.id,n.sort_order,CONCAT('/api/media/',n.image_path) image_url FROM news_page n JOIN source_file s ON s.id=n.source_file_id WHERE n.play_status='PLAYING' AND s.convert_status='SUCCESS' ORDER BY n.sort_order,n.id"); }

  @GetMapping({"/config","/display/config"})
  public Map<String,Object> config(HttpSession session, jakarta.servlet.http.HttpServletRequest request) {
    if (request.getRequestURI().equals("/api/config")) admin(session);
    return db.queryForMap("SELECT display_seconds,animation_enabled,animation_type,animation_duration,logo_enabled,logo_position,IF(logo_path IS NULL,NULL,CONCAT('/api/media/',logo_path)) logo_url,IF(default_image_path IS NULL,NULL,CONCAT('/api/media/',default_image_path)) default_image_url FROM display_config WHERE id=1");
  }

  @PutMapping("/config")
  public Map<String,Object> configUpdate(@RequestBody Map<String,Object> body, HttpSession session) {
    long adminId = admin(session);
    int seconds = ((Number)body.getOrDefault("displaySeconds",10)).intValue();
    double duration = ((Number)body.getOrDefault("animationDuration",0.8)).doubleValue();
    String position = String.valueOf(body.getOrDefault("logoPosition","top-left"));
    if (seconds < 1 || seconds > 3600 || duration < 0 || duration >= seconds || !List.of("top-left","top-right","bottom-left","bottom-right").contains(position)) throw new IllegalArgumentException("展示参数无效");
    db.update("UPDATE display_config SET display_seconds=?,animation_enabled=?,animation_type='fade',animation_duration=?,logo_enabled=?,logo_position=? WHERE id=1", seconds, body.getOrDefault("animationEnabled",true), duration, body.getOrDefault("logoEnabled",true), position);
    log.info("更新展示设置 adminId={} displaySeconds={} animationEnabled={} animationDuration={} logoEnabled={} logoPosition={}", adminId, seconds, body.getOrDefault("animationEnabled",true), duration, body.getOrDefault("logoEnabled",true), position);
    return Map.of("ok",true);
  }

  @PostMapping({"/config/logo","/config/default-image"})
  public Map<String,Object> image(@RequestParam MultipartFile file, HttpSession session, jakarta.servlet.http.HttpServletRequest request) throws IOException {
    long adminId = admin(session);
    if (file.isEmpty() || file.getSize() > 10_000_000) throw new IllegalArgumentException("图片为空或超过 10MB");
    java.awt.image.BufferedImage check = javax.imageio.ImageIO.read(file.getInputStream());
    if (check == null || check.getWidth() > 8000 || check.getHeight() > 8000) throw new IllegalArgumentException("请上传尺寸合适的 PNG 或 JPEG 图片");
    String relative = "assets/" + UUID.randomUUID() + ".png";
    String old = request.getRequestURI().endsWith("logo") ? "logo_path" : "default_image_path";
    javax.imageio.ImageIO.write(check, "png", storage.resolve(relative).toFile());
    db.update("UPDATE display_config SET " + old + "=? WHERE id=1", relative);
    log.info("更新展示图片 adminId={} type={} size={} path={}", adminId, old, file.getSize(), relative);
    return Map.of("url", "/api/media/" + relative);
  }

  @GetMapping("/media/{folder}/{name}")
  public ResponseEntity<byte[]> media(@PathVariable String folder, @PathVariable String name) throws IOException {
    if (!List.of("pages","thumbnails","assets").contains(folder)) throw new IllegalArgumentException("资源路径无效");
    Path file = storage.resolve(folder).resolve(name).normalize();
    if (!file.startsWith(storage.resolve(folder)) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public,max-age=60").contentType(MediaType.parseMediaType(name.endsWith(".webp") ? "image/webp" : name.endsWith(".png") ? "image/png" : "image/jpeg")).body(Files.readAllBytes(file));
  }

  @GetMapping("/media/{folder}/{id}/{name}")
  public ResponseEntity<byte[]> mediaNested(@PathVariable String folder,@PathVariable String id,@PathVariable String name) throws IOException {
    if (!List.of("pages","thumbnails").contains(folder) || !id.matches("[0-9]+") || !name.matches("(page|thumb)_[0-9]{3}\\.webp")) throw new IllegalArgumentException("资源路径无效");
    Path file = storage.resolve(folder).resolve(id).resolve(name).normalize();
    if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public,max-age=60").contentType(MediaType.parseMediaType("image/webp")).body(Files.readAllBytes(file));
  }

  private void renumber() {
    List<Long> ids = db.queryForList("SELECT id FROM news_page WHERE play_status='PLAYING' ORDER BY sort_order,id",Long.class);
    for (int i=0;i<ids.size();i++) db.update("UPDATE news_page SET sort_order=? WHERE id=?",i+1,ids.get(i));
  }

  @PutMapping("/news/{id}/play")
  public Map<String,Object> play(@PathVariable long id,HttpSession session) {
    long adminId = admin(session);
    tx.executeWithoutResult(s -> {
      Integer ready = db.queryForObject("SELECT COUNT(*) FROM news_page n JOIN source_file f ON f.id=n.source_file_id WHERE n.id=? AND f.convert_status='SUCCESS'",Integer.class,id);
      if (ready == null || ready == 0) throw new IllegalArgumentException("新闻不存在或转换未完成");
      Integer next = db.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM news_page WHERE play_status='PLAYING'",Integer.class);
      db.update("UPDATE news_page SET play_status='PLAYING',sort_order=? WHERE id=? AND play_status='OFFLINE'",next,id);
    });
    log.info("新闻加入播放 adminId={} newsId={}", adminId, id);
    return Map.of("ok",true);
  }

  @PutMapping("/news/{id}/offline")
  public Map<String,Object> offline(@PathVariable long id,HttpSession session) {
    long adminId = admin(session);
    tx.executeWithoutResult(s -> { db.update("UPDATE news_page SET play_status='OFFLINE',sort_order=0 WHERE id=?",id); renumber(); });
    log.info("新闻下架 adminId={} newsId={}", adminId, id);
    return Map.of("ok",true);
  }

  @PutMapping("/news/play-all")
  public Map<String,Object> playAll(HttpSession session) {
    long adminId = admin(session);
    int count=tx.execute(s -> {
      List<Long> ids=db.queryForList("SELECT n.id FROM news_page n JOIN source_file f ON f.id=n.source_file_id WHERE n.play_status='OFFLINE' AND f.convert_status='SUCCESS' ORDER BY f.created_at,f.id,n.page_number",Long.class);
      Integer next=db.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM news_page WHERE play_status='PLAYING'",Integer.class);
      for(Long id:ids) db.update("UPDATE news_page SET play_status='PLAYING',sort_order=? WHERE id=?",next++,id);
      return ids.size();
    });
    log.info("全部新闻加入播放 adminId={} addedCount={}", adminId, count);
    return Map.of("count",count);
  }

  @PutMapping("/news/offline-all")
  public Map<String,Object> offlineAll(HttpSession session) { long adminId=admin(session); int n=db.update("UPDATE news_page SET play_status='OFFLINE',sort_order=0 WHERE play_status='PLAYING'"); log.info("全部新闻下架 adminId={} count={}",adminId,n); return Map.of("count",n); }

  @PutMapping("/news/sort")
  public Map<String,Object> sort(@RequestBody Map<String,List<Long>> body,HttpSession session) {
    long adminId = admin(session);
    List<Long> ids=body.getOrDefault("ids",List.of());
    tx.executeWithoutResult(s -> {
      List<Long> current=db.queryForList("SELECT id FROM news_page WHERE play_status='PLAYING'",Long.class);
      if (ids.size()!=current.size() || !new java.util.HashSet<>(ids).equals(new java.util.HashSet<>(current))) throw new IllegalArgumentException("播放列表已变化，请刷新后重试");
      for(int i=0;i<ids.size();i++) db.update("UPDATE news_page SET sort_order=? WHERE id=?",i+1,ids.get(i));
    });
    log.info("更新播放顺序 adminId={} newsIds={}", adminId, ids);
    return Map.of("ok",true);
  }

  @DeleteMapping("/news/{id}")
  public Map<String,Object> deleteNews(@PathVariable long id,HttpSession session) {
    long adminId = admin(session);
    List<Map<String,Object>> rows=db.queryForList("SELECT image_path,thumbnail_path FROM news_page WHERE id=?",id);
    if(rows.isEmpty()) throw new IllegalArgumentException("新闻不存在");
    tx.executeWithoutResult(s -> { db.update("DELETE FROM news_page WHERE id=?",id); renumber(); });
    for(String key:List.of("image_path","thumbnail_path")) try { Files.deleteIfExists(storage.resolve(String.valueOf(rows.get(0).get(key)))); } catch(IOException ignored) {}
    log.info("删除新闻 adminId={} newsId={}", adminId, id);
    return Map.of("ok",true);
  }

  @DeleteMapping("/files/{id}")
  public Map<String,Object> deleteFile(@PathVariable long id,HttpSession session) {
    long adminId = admin(session);
    List<Map<String,Object>> files=db.queryForList("SELECT file_path,convert_status FROM source_file WHERE id=?",id);
    if(files.isEmpty()) throw new IllegalArgumentException("文件不存在");
    if("PROCESSING".equals(files.get(0).get("convert_status"))) throw new IllegalArgumentException("转换中，请稍后删除");
    List<Map<String,Object>> pages=db.queryForList("SELECT image_path,thumbnail_path FROM news_page WHERE source_file_id=?",id);
    tx.executeWithoutResult(s -> { db.update("DELETE FROM news_page WHERE source_file_id=?",id); db.update("DELETE FROM source_file WHERE id=?",id); renumber(); });
    try { Files.deleteIfExists(storage.resolve(String.valueOf(files.get(0).get("file_path")))); } catch(IOException ignored) {}
    for(Map<String,Object> p:pages) for(String key:List.of("image_path","thumbnail_path")) try { Files.deleteIfExists(storage.resolve(String.valueOf(p.get(key)))); } catch(IOException ignored) {}
    log.info("删除来源文件 adminId={} fileId={} pageCount={}", adminId, id, pages.size());
    return Map.of("ok",true);
  }
}

package shanghan.early;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.*;
import java.util.*;
import static org.lwjgl.opengl.GL32C.*;
/** Owns only its textures, program and VAO. All touched game GL state is restored. */
public final class VideoRenderer implements AutoCloseable {
 private final MediaSession media;
 private int program,vao,videoTexture,videoU,videoV,uiTexture,fallbackTexture,alphaUniform;
 private ByteBuffer pixels,uiPixels;
 private BufferedImage fallback;
 private Theme theme;
 private record Assets(Theme theme,BufferedImage fallback){}
 private volatile Assets pendingAssets;
 private Thread assetThread;
 private boolean assetsApplied;
 private BufferedImage uiImage=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);
 private String lastUiContent="";

 private int modeUniform;
 private long lastUi,lastStats,lastUiPoll;
 private java.util.List<ModTaskObserver.Task> uiTasks=java.util.List.of();
 private volatile boolean menuReady;
 private int menuTexture;
 private final int[] savedTextures=new int[3],savedViewport=new int[4];
 private boolean hasVideo,initialized;
 private volatile boolean closed;
 private long lastPresented,statsOrigin,rendered,lastRendered;
 public VideoRenderer(MediaSession media){this.media=media;assetThread=new Thread(()->{try{Theme t=new Theme(media);BufferedImage f=t.image(media.settings.fallback);if(!closed)pendingAssets=new Assets(t,f);}catch(Throwable e){media.log("THEME_FALLBACK "+e);}},"LoadingFramework-assets");assetThread.setDaemon(true);assetThread.start();}
 private int shader(int type,String source){
  int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);
  if(glGetShaderi(s,GL_COMPILE_STATUS)==0){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;
 }
 private void init(){
  int v=shader(GL_VERTEX_SHADER,"#version 150\nout vec2 uv;\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0,1);}");
  int f=shader(GL_FRAGMENT_SHADER,"#version 150\nuniform sampler2D image;uniform sampler2D planeU;uniform sampler2D planeV;uniform int videoMode;uniform float opacity;in vec2 uv;out vec4 color;void main(){if(videoMode==1){float y=1.16438356*(texture(image,uv).r-16.0/255.0);float u=texture(planeU,uv).r-128.0/255.0;float v=texture(planeV,uv).r-128.0/255.0;color=vec4(y+1.79274107*v,y-0.21324861*u-0.53290933*v,y+2.11240179*u,1.0);}else if(videoMode==2){color=texture(image,vec2(uv.x,1.0-uv.y));color.a=1.0;}else{color=texture(image,uv);}color.a*=opacity;}");
  program=glCreateProgram();glAttachShader(program,v);glAttachShader(program,f);glLinkProgram(program);glDeleteShader(v);glDeleteShader(f);
  if(glGetProgrami(program,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(program));
  vao=glGenVertexArrays();videoTexture=texture();videoU=texture();videoV=texture();uiTexture=texture();fallbackTexture=texture();
  glUseProgram(program);glUniform1i(glGetUniformLocation(program,"image"),0);alphaUniform=glGetUniformLocation(program,"opacity");modeUniform=glGetUniformLocation(program,"videoMode");glUniform1i(glGetUniformLocation(program,"planeU"),1);glUniform1i(glGetUniformLocation(program,"planeV"),2);
  pixels=MemoryUtil.memAlloc(media.settings.width*media.settings.height*3/2);uiPixels=MemoryUtil.memAlloc(1280*720*4);
  theme=new Theme(media,false);
  if(fallback==null){
   fallback=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);
   Graphics2D g=fallback.createGraphics();g.setPaint(new GradientPaint(0,0,theme.background().brighter(),1280,720,theme.background().darker()));g.fillRect(0,0,1280,720);g.dispose();
  }
  // Cap fallback allocation by rendering it to the fixed UI canvas.
  BufferedImage scaled=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);
  Graphics2D sg=scaled.createGraphics();sg.drawImage(fallback,0,0,1280,720,null);sg.dispose();fallback=scaled;
  rgba(fallback,uiPixels);glBindTexture(GL_TEXTURE_2D,fallbackTexture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1280,720,0,GL_RGBA,GL_UNSIGNED_BYTE,uiPixels);
  glBindTexture(GL_TEXTURE_2D,uiTexture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1280,720,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
  initialized=true;statsOrigin=System.nanoTime();lastStats=statsOrigin;media.log("RENDER_READY");
 }
 private int texture(){
  int t=glGenTextures();glBindTexture(GL_TEXTURE_2D,t);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return t;
 }
 private static void rgba(BufferedImage image,ByteBuffer buffer){
  buffer.clear();buffer.order(ByteOrder.BIG_ENDIAN);IntBuffer out=buffer.asIntBuffer();
  // Our UI and scaled fallback canvases are contiguous TYPE_INT_ARGB.
  int[] argb=((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();
  for(int value:argb)out.put(Integer.rotateLeft(value,8));
  buffer.position(0).limit(argb.length*4);
 }
 private void applyAssets(){
  Assets a=pendingAssets;if(a==null||assetsApplied)return;assetsApplied=true;pendingAssets=null;theme=a.theme();lastUiContent="";
  if(a.fallback()!=null){
   BufferedImage scaled=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);Graphics2D g=scaled.createGraphics();g.drawImage(a.fallback(),0,0,1280,720,null);g.dispose();
   rgba(scaled,uiPixels);glBindTexture(GL_TEXTURE_2D,fallbackTexture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,1280,720,GL_RGBA,GL_UNSIGNED_BYTE,uiPixels);
  }
 }
 private void updateUi(){
  BufferedImage image=uiImage;Graphics2D g=image.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,1280,720);g.setComposite(AlphaComposite.SrcOver);
  theme.draw(g,media.progressState(),uiTasks);g.dispose();
  rgba(image,uiPixels);glBindTexture(GL_TEXTURE_2D,uiTexture);
  glTexSubImage2D(GL_TEXTURE_2D,0,0,0,1280,720,GL_RGBA,GL_UNSIGNED_BYTE,uiPixels);
 }
 public void render(int width,int height,float alpha){
  if(closed||width<1||height<1)return;
  rendered++;
  // The early context belongs exclusively to this renderer. Query/restore state only in the game context.
  boolean isolated=media.detached;
  int oldProgram=0,oldVao=0,oldActive=GL_TEXTURE0,oldPbo=0,oldUnpack=4,oldRow=0;
  int srcRgb=GL_ONE,dstRgb=GL_ZERO,srcA=GL_ONE,dstA=GL_ZERO,eqRgb=GL_FUNC_ADD,eqA=GL_FUNC_ADD;
  boolean srgb=false,blend=false,depth=false,cull=false,scissor=false;
  int[] oldTextures=savedTextures,viewport=savedViewport;
  if(!isolated){
   oldProgram=glGetInteger(GL_CURRENT_PROGRAM);oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING);oldActive=glGetInteger(GL_ACTIVE_TEXTURE);
   for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);oldTextures[i]=glGetInteger(GL_TEXTURE_BINDING_2D);}
   oldPbo=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);oldUnpack=glGetInteger(GL_UNPACK_ALIGNMENT);oldRow=glGetInteger(GL_UNPACK_ROW_LENGTH);
   srcRgb=glGetInteger(GL_BLEND_SRC_RGB);dstRgb=glGetInteger(GL_BLEND_DST_RGB);srcA=glGetInteger(GL_BLEND_SRC_ALPHA);dstA=glGetInteger(GL_BLEND_DST_ALPHA);
   eqRgb=glGetInteger(GL_BLEND_EQUATION_RGB);eqA=glGetInteger(GL_BLEND_EQUATION_ALPHA);
   srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB);blend=glIsEnabled(GL_BLEND);depth=glIsEnabled(GL_DEPTH_TEST);cull=glIsEnabled(GL_CULL_FACE);scissor=glIsEnabled(GL_SCISSOR_TEST);
   glGetIntegerv(GL_VIEWPORT,viewport);
  }
  glActiveTexture(GL_TEXTURE0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
  try{
   glPixelStorei(GL_UNPACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);
   if(program==0)init();
   applyAssets();
   glDisable(GL_FRAMEBUFFER_SRGB);
   glViewport(0,0,width,height);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDisable(GL_SCISSOR_TEST);
   glEnable(GL_BLEND);glBlendEquation(GL_FUNC_ADD);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
   glUseProgram(program);glBindVertexArray(vao);
   if(isolated&&menuReady){
    glDisable(GL_BLEND);glUniform1f(alphaUniform,1);glUniform1i(modeUniform,2);
    glBindTexture(GL_TEXTURE_2D,menuTexture);glDrawArrays(GL_TRIANGLES,0,3);glEnable(GL_BLEND);
   }
   glUniform1f(alphaUniform,alpha);
   MediaSession.Frame frame=media.latest();
   if(frame!=null){
    try{
     pixels.clear();pixels.put(frame.rgba()).flip();
     int w=media.settings.width,h=media.settings.height,yBytes=w*h,cBytes=yBytes/4;
     plane(videoTexture,0,w,h,0,yBytes);
     plane(videoU,1,w/2,h/2,yBytes,cBytes);
     plane(videoV,2,w/2,h/2,yBytes+cBytes,cBytes);
     hasVideo=true;
    }finally{media.release(frame);}
   }
   glActiveTexture(GL_TEXTURE0);
   boolean useVideo=hasVideo&&!media.videoFailed;glUniform1i(modeUniform,useVideo?1:0);
   if(useVideo){
    // Rebind chroma planes even when this tick repeats the previous frame.
    glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,videoU);
    glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,videoV);
    glActiveTexture(GL_TEXTURE0);
   }
   glBindTexture(GL_TEXTURE_2D,useVideo?videoTexture:fallbackTexture);glDrawArrays(GL_TRIANGLES,0,3);
   long now=System.nanoTime();
   if(now-lastUiPoll>=100_000_000L||lastUiPoll==0){
    uiTasks=media.tasks.current();
    String content=media.progressState().toString()+uiTasks.stream().map(media.tasks::label).toList();
    if(!content.equals(lastUiContent)){updateUi();lastUi=now;lastUiContent=content;}
    lastUiPoll=now;
   }
   glUniform1i(modeUniform,0);
   glBindTexture(GL_TEXTURE_2D,uiTexture);glDrawArrays(GL_TRIANGLES,0,3);
   if(now-lastStats>2_000_000_000L){
    double fps=(media.presented-lastPresented)*1e9/(now-lastStats);
    media.log("STATS renderFPS="+String.format(Locale.ROOT,"%.2f",(rendered-lastRendered)*1e9/(now-lastStats))+" videoFPS="+String.format(Locale.ROOT,"%.2f",fps)+" queue="+media.frames.size()+" clock="+String.format(Locale.ROOT,"%.3f",media.clock())+" progress="+media.progress.value+" stage="+media.progress.stage);
    media.log("CURRENT_TASKS "+media.tasks.current().stream().map(media.tasks::label).toList()+" observed="+media.tasks.started.get()+" ended="+media.tasks.ended.get()+" examples="+media.tasks.samples());
    lastPresented=media.presented;lastRendered=rendered;lastStats=now;
   }
  }catch(Throwable e){media.videoFailed=true;media.log("RENDER_ERROR "+e);if(!initialized)throw new IllegalStateException("Loading renderer initialization failed",e);}
  finally{
   if(!isolated){
   glUseProgram(oldProgram);glBindVertexArray(oldVao);for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,oldTextures[i]);}glActiveTexture(oldActive);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,oldPbo);
   glPixelStorei(GL_UNPACK_ALIGNMENT,oldUnpack);glPixelStorei(GL_UNPACK_ROW_LENGTH,oldRow);
   glBlendFuncSeparate(srcRgb,dstRgb,srcA,dstA);glBlendEquationSeparate(eqRgb,eqA);
   enable(GL_FRAMEBUFFER_SRGB,srgb);enable(GL_BLEND,blend);enable(GL_DEPTH_TEST,depth);enable(GL_CULL_FACE,cull);enable(GL_SCISSOR_TEST,scissor);
   glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
   }
  }
 }
 /** GPU-only one-time snapshot of the prepared menu. Context sharing makes the texture available to the opaque early window. */
 public void captureMenu(int width,int height){
  if(!media.detached||menuReady||width<1||height<1)return;
  int read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
  int active=glGetInteger(GL_ACTIVE_TEXTURE);glActiveTexture(GL_TEXTURE0);int texture=glGetInteger(GL_TEXTURE_BINDING_2D);
  int pbo=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
  int fbo=0,newTexture=0;boolean srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB),scissor=glIsEnabled(GL_SCISSOR_TEST);
  try{
   double scale=Math.min(1,Math.min(1280.0/width,720.0/height));
   int w=Math.max(1,(int)Math.round(width*scale)),h=Math.max(1,(int)Math.round(height*scale));
   newTexture=texture();glBindTexture(GL_TEXTURE_2D,newTexture);
   glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
   fbo=glGenFramebuffers();glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);
   glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,newTexture,0);
   if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Menu framebuffer incomplete");
   glDisable(GL_FRAMEBUFFER_SRGB);glDisable(GL_SCISSOR_TEST);
   glBlitFramebuffer(0,0,width,height,0,0,w,h,GL_COLOR_BUFFER_BIT,GL_LINEAR);
   int error=glGetError();if(error!=GL_NO_ERROR)throw new IllegalStateException("Menu GPU copy error "+error);
   glFinish();menuTexture=newTexture;menuReady=true;newTexture=0;
   media.log("MENU_CAPTURE_READY size="+w+"x"+h);
  }catch(Throwable error){media.log("MENU_CAPTURE_WARNING "+error);}
  finally{
   glBindFramebuffer(GL_READ_FRAMEBUFFER,read);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);
   if(fbo!=0)glDeleteFramebuffers(fbo);if(newTexture!=0)glDeleteTextures(newTexture);
   glBindTexture(GL_TEXTURE_2D,texture);glActiveTexture(active);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);enable(GL_FRAMEBUFFER_SRGB,srgb);enable(GL_SCISSOR_TEST,scissor);
  }
 }
 private void plane(int texture,int unit,int width,int height,int offset,int bytes){
  glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,texture);pixels.limit(offset+bytes).position(offset);
  if(!hasVideo)glTexImage2D(GL_TEXTURE_2D,0,GL_R8,width,height,0,GL_RED,GL_UNSIGNED_BYTE,pixels);
  else glTexSubImage2D(GL_TEXTURE_2D,0,0,0,width,height,GL_RED,GL_UNSIGNED_BYTE,pixels);
 }
 private static void enable(int flag,boolean on){if(on)glEnable(flag);else glDisable(flag);}
 public void close(){
  if(closed)return;closed=true;if(assetThread!=null){assetThread.interrupt();try{assetThread.join(300);}catch(InterruptedException e){Thread.currentThread().interrupt();}}pendingAssets=null;fallback=null;theme=null;
  if(menuTexture!=0)glDeleteTextures(menuTexture);menuReady=false;
  if(videoTexture!=0)glDeleteTextures(videoTexture);if(videoU!=0)glDeleteTextures(videoU);if(videoV!=0)glDeleteTextures(videoV);if(uiTexture!=0)glDeleteTextures(uiTexture);if(fallbackTexture!=0)glDeleteTextures(fallbackTexture);
  if(vao!=0)glDeleteVertexArrays(vao);if(program!=0)glDeleteProgram(program);
  if(pixels!=null)MemoryUtil.memFree(pixels);if(uiPixels!=null)MemoryUtil.memFree(uiPixels);pixels=null;uiPixels=null;uiImage=null;uiTasks=java.util.List.of();assetThread=null;media.close();
 }
}

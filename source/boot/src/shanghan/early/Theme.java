package shanghan.early;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;
/** Logical 1280x720 canvas, scaled once by GL. External fonts/images are loaded once with bounded input sizes. */
public final class Theme {
 public static final class Text {
  public String text="",font="",color="#FFFFFF",weight="normal",anchor="top-left",x="50",y="440";
  public int size=19;public float opacity=1;public boolean enabled=true;
  Text(){}Text(String text,int size,String y){this.text=text;this.size=size;this.y=y;}
 }
 public static final class Box {
  public String x="50",y="647",width="1080",height="10",anchor="top-left",color="#A9DDAE",background="#FFFFFF37";
  public int radius=10;public float opacity=1;public boolean enabled=true;
 }
 public static final class Config {
  public Text title=new Text("${packName}",36,"440"),subtitle=new Text("${subtitle}",19,"481"),status=new Text("当前正在加载："+ "${stage}",23,"516"),tasks=new Text("",19,"551"),percent=new Text("${stagePercent}",23,"640"),mods=new Text("${modCount}",15,"618");
  public Box progress=new Box(),logo=new Box();
  public boolean dimBackground=true;public String background="#0C1713";public float shadeOpacity=.86f;
  public int maxTasks=3,taskSpacing=25;
  public Config(){title.weight="bold";status.weight="bold";percent.weight="bold";percent.x="1150";logo.x="50";logo.y="300";logo.width="96";logo.height="96";}
  public void normalize(){Config d=new Config();if(title==null)title=d.title;if(subtitle==null)subtitle=d.subtitle;if(status==null)status=d.status;if(tasks==null)tasks=d.tasks;if(percent==null)percent=d.percent;if(mods==null)mods=d.mods;if(progress==null)progress=d.progress;if(logo==null)logo=d.logo;maxTasks=Math.max(1,Math.min(12,maxTasks));taskSpacing=Math.max(12,Math.min(100,taskSpacing));}
 }
 private final MediaSession media;private final Config cfg;private final boolean external;private final Map<String,Font> fonts=new HashMap<>();
 private final Map<Text,Font> derived=new IdentityHashMap<>();
 private final BufferedImage logo;private final Font defaultFont=new Font(Font.SANS_SERIF,Font.PLAIN,19);
 public Theme(MediaSession media){this(media,true);}
 public Theme(MediaSession media,boolean external){this.media=media;this.external=external;cfg=media.settings.ui;cfg.normalize();logo=external?image(media.settings.logo):null;for(Text t:new Text[]{cfg.title,cfg.subtitle,cfg.status,cfg.tasks,cfg.percent,cfg.mods})font(t);}
 public BufferedImage image(String name){
  try{
   if(name==null||name.isBlank())return null;Path p=media.settings.resource(media.dir,name);if(!Files.isRegularFile(p))return null;
   if(Files.size(p)>32_000_000)throw new IllegalArgumentException("Image exceeds 32 MB");
   try(ImageInputStream in=ImageIO.createImageInputStream(p.toFile())){
    var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Unsupported image");var reader=readers.next();
    try{reader.setInput(in);long w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>4096||h>4096||w*h>8_000_000)throw new IllegalArgumentException("Image dimensions too large");return reader.read(0);}finally{reader.dispose();}
   }
  }catch(Exception e){media.log("IMAGE_FALLBACK "+name+" "+e);return null;}
 }
 public Color background(){return color(cfg.background,new Color(12,23,19));}
 private Font font(Text t){
  if(derived.containsKey(t))return derived.get(t);
  String path=t.font==null||t.font.isBlank()?media.settings.font:t.font;Font base=defaultFont;
  if(external&&path!=null&&!path.isBlank()){
   if(!fonts.containsKey(path)){
    try{Path f=media.settings.resource(media.dir,path);if(Files.size(f)>8_000_000)throw new IllegalArgumentException("Font exceeds 8 MB");try(var in=Files.newInputStream(f)){fonts.put(path,Font.createFont(Font.TRUETYPE_FONT,in));}}
    catch(Exception e){media.log("FONT_FALLBACK "+path+" "+e);fonts.put(path,defaultFont);}
   }base=fonts.get(path);
  }
  Font result=base.deriveFont("bold".equalsIgnoreCase(t.weight)?Font.BOLD:Font.PLAIN,(float)Math.max(8,Math.min(180,t.size)));derived.put(t,result);return result;
 }
 private static int dim(String value,int extent,int fallback){try{double n=Double.parseDouble(value.endsWith("%")?value.substring(0,value.length()-1):value);if(!Double.isFinite(n))return fallback;return (int)Math.round(value.endsWith("%")?extent*n/100:n);}catch(Exception e){return fallback;}}
 private static Color color(String text,Color fallback){try{String h=text.replace("#","");long v=Long.parseLong(h,16);return h.length()==8?new Color((int)(v>>24)&255,(int)(v>>16)&255,(int)(v>>8)&255,(int)v&255):h.length()==6?new Color((int)v):fallback;}catch(Exception e){return fallback;}}
 private static float alpha(float v){return Float.isFinite(v)?Math.max(0,Math.min(1,v)):1;}
 private static int anchorX(String a,int x,int width){if(a==null)return x;return a.contains("center")?x-width/2:a.endsWith("right")?x-width:x;}
 private static int anchorY(String a,int y,int height){if(a==null)return y;return a.startsWith("bottom")?y-height:a.equals("center")?y-height/2:y;}
 private String expand(String text,ProgressProvider.Snapshot p){
  String count=p.totalMods()>0?"已发现 "+p.discoveredMods()+" / "+p.totalMods()+" 个模组":"";
  String phase=p.stageFraction()>=0?(int)Math.floor(p.stageFraction()*100)+"%":"";
  return (text==null?"":text).replace("${packName}",media.settings.title).replace("${subtitle}",media.settings.subtitle==null?"":media.settings.subtitle).replace("${stage}",p.stage()).replace("${stagePercent}",phase).replace("${modCount}",count).replace("${sourceLabel}",p.sourceLabel());
 }
 private void text(Graphics2D g,Text t,String value,int offset){
  if(!t.enabled||value.isBlank())return;g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha(t.opacity)));g.setFont(font(t));g.setColor(color(t.color,Color.WHITE));
  var metrics=g.getFontMetrics();int width=metrics.stringWidth(value),x=dim(t.x,1280,50),y=dim(t.y,720,440)+offset;
  int available=Math.max(50,1280-Math.max(0,x)-30);
  if(t.anchor==null||t.anchor.endsWith("left")){while(width>available&&value.length()>2){value=value.substring(0,value.length()-2)+"…";width=metrics.stringWidth(value);}}
  g.drawString(value,anchorX(t.anchor,x,width),anchorY(t.anchor,y,metrics.getHeight())+metrics.getAscent());
 }
 public void draw(Graphics2D g,ProgressProvider.Snapshot state,java.util.List<ModTaskObserver.Task> current){
  g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  if(cfg.dimBackground){g.setComposite(AlphaComposite.SrcOver);g.setPaint(new GradientPaint(0,380,new Color(0,0,0,0),0,720,new Color(0,0,0,(int)(255*alpha(cfg.shadeOpacity)))));g.fillRect(0,380,1280,340);}
  if(logo!=null&&cfg.logo.enabled){Box b=cfg.logo;int w=Math.max(1,dim(b.width,1280,96)),h=Math.max(1,dim(b.height,720,96));g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha(b.opacity)));g.drawImage(logo,anchorX(b.anchor,dim(b.x,1280,50),w),anchorY(b.anchor,dim(b.y,720,300),h),w,h,null);}
  for(Text t:new Text[]{cfg.title,cfg.subtitle,cfg.status,cfg.mods,cfg.percent})text(g,t,expand(t.text,state),0);
  int i=0;for(var task:current){if(i>=cfg.maxTasks)break;text(g,cfg.tasks,media.tasks.label(task),i++*cfg.taskSpacing);}
  Box b=cfg.progress;if(b.enabled){
   int w=Math.max(1,dim(b.width,1280,1080)),h=Math.max(1,dim(b.height,720,10)),x=anchorX(b.anchor,dim(b.x,1280,50),w),y=anchorY(b.anchor,dim(b.y,720,647),h);
   g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha(b.opacity)));g.setColor(color(b.background,new Color(255,255,255,55)));g.fillRoundRect(x,y,w,h,b.radius,b.radius);
   g.setColor(color(b.color,new Color(169,221,174)));g.fillRoundRect(x,y,(int)(w*Math.max(0,Math.min(1,state.overall()))),h,b.radius,b.radius);
  }g.setComposite(AlphaComposite.SrcOver);
 }
}

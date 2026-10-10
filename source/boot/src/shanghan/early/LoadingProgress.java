package shanghan.early;
import net.minecraftforge.fml.loading.progress.*;
import java.util.*;
/** No timer-based progress. Overall mapping is an estimate; stageFraction is the actual published meter/reload fraction. */
public final class LoadingProgress implements ProgressProvider {
 public volatile double value;
 public volatile String stage="正在检查游戏环境";
 private int reached,discovered,total;
 private double stageFraction=-1;
 private String sourceLabel="";
 private static final String[] TITLES={"正在检查游戏环境","正在发现模组","正在初始化模组","正在注册游戏内容","正在加载客户端资源","正在构建纹理","正在加载模型","正在完成初始化"};
 private static final double[] START={0,.04,.12,.30,.50,.74,.84,.97};
 public synchronized void mods(int count){discovered=count;total=count;}
 public synchronized void tick(){
  var bars=StartupNotificationManager.getCurrentProgress();StringBuilder text=new StringBuilder();
  for(var p:bars)text.append(' ').append(p.name()).append(' ').append(p.label().getText());
  String raw=text.toString().toLowerCase(Locale.ROOT);int signal=0;
  if(raw.contains("discover")||raw.contains("scan"))signal=1;
  if(raw.contains("construct")||raw.contains("loading mods")||raw.contains("initializ"))signal=2;
  if(raw.contains("register")||raw.contains("registry")||raw.contains("common_setup"))signal=3;
  if(raw.contains("minecraft progress"))signal=4;
  if(raw.contains("texture")||raw.contains("stitch"))signal=5;
  if(raw.contains("model")||raw.contains("baking"))signal=6;
  if(signal>reached){reached=signal;stageFraction=-1;}stage=TITLES[reached];
  double fraction=-1;sourceLabel="";
  for(var p:bars){String label=p.label().getText();if(label!=null&&!label.isBlank())sourceLabel=label;if(p.steps()>0&&reached<4){double f=p.progress();if(Double.isFinite(f))fraction=Math.max(fraction,Math.max(0,Math.min(1,f)));}}
  if(reached<4)stageFraction=fraction;
  double end=reached+1<START.length?START[reached+1]:.99;
  value=Math.max(value,Math.min(.99,START[reached]+(end-START[reached])*Math.max(0,fraction)));
 }
 public synchronized void resource(float fraction){
  reached=Math.max(reached,4);stage=TITLES[reached];
  if(Float.isFinite(fraction)){stageFraction=Math.max(0,Math.min(1,fraction));value=Math.max(value,.50+.47*stageFraction);}
 }
 public synchronized void complete(){value=1;reached=7;stage="初始化完成";stageFraction=1;}
 public synchronized Snapshot snapshot(){return new Snapshot(value,stage,value==1?Accuracy.COMPLETE:stageFraction>=0?Accuracy.STAGE_FRACTION:Accuracy.ESTIMATE,stageFraction,discovered,total,sourceLabel);}
}

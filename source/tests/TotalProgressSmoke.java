import shanghan.early.*;
import java.nio.file.*;
import java.lang.reflect.*;
public final class TotalProgressSmoke {
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);Files.createDirectories(dir);
  Files.writeString(dir.resolve("config.json"),"{\"ui\":{\"percent\":{\"text\":\"${stagePercent}\"}}}");
  Settings settings=Settings.load(dir);
  if(!settings.ui.percent.text.equals("${overallPercent}"))throw new AssertionError("Legacy stage label was not migrated");
  MediaSession media=new MediaSession(dir,settings);
  try{
   Theme theme=new Theme(media,false);
   Method expand=Theme.class.getDeclaredMethod("expand",String.class,ProgressProvider.Snapshot.class);expand.setAccessible(true);
   ProgressProvider.Snapshot different=new ProgressProvider.Snapshot(.45,"Example",ProgressProvider.Accuracy.STAGE_FRACTION,.90,10,10,"");
   String text=(String)expand.invoke(theme,settings.ui.percent.text,different);
   if(!text.equals("45%"))throw new AssertionError("Default percentage must agree with the overall bar, got "+text);
   LoadingProgress progress=new LoadingProgress();double last=0;
   for(float fraction:new float[]{0,.25f,.10f,.80f,.20f,1}){
    progress.resource(fraction);ProgressProvider.Snapshot state=progress.snapshot();
    if(state.overall()<last||state.overall()>=1)throw new AssertionError("Overall reset or completed early");
    String shown=(String)expand.invoke(theme,settings.ui.percent.text,state);
    if(!shown.equals((int)Math.floor(state.overall()*100)+"%"))throw new AssertionError("Bar/text disagreement: "+shown);
    last=state.overall();
   }
   progress.complete();
   if(!expand.invoke(theme,settings.ui.percent.text,progress.snapshot()).equals("100%"))throw new AssertionError("Final completion missing");
  }finally{media.close();media.finishLogging();}
  System.out.println("PASS overall percent matches the bar, no reset during resource stages, stays below 100 until complete.");
 }
}

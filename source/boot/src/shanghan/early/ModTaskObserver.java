package shanghan.early;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/**
 * Exact 47.3.22 / 47.3.33 FMLModContainer structured TRACE begin/end messages.
 * The appender performs no file I/O and never throws into Forge.
 * Constructor and event-dispatch intervals are real; deferred work/resources are explicitly not attributed.
 */
public final class ModTaskObserver extends AbstractAppender implements AutoCloseable {
 public record Task(String key,String id,String step,long start,String thread){}
 private final ConcurrentHashMap<String,Task> active=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,String> names=new ConcurrentHashMap<>();
 private final ConcurrentLinkedDeque<String> samples=new ConcurrentLinkedDeque<>();
 private final Map<org.apache.logging.log4j.core.Logger,Level> attached=new IdentityHashMap<>();
 private final Map<org.apache.logging.log4j.core.Logger,Boolean> additive=new IdentityHashMap<>();
 private final Map<org.apache.logging.log4j.core.Logger,Boolean> ownConfig=new IdentityHashMap<>();
 private record Suppression(org.apache.logging.log4j.core.config.LoggerConfig parent,org.apache.logging.log4j.core.Filter filter){}
 private final Map<org.apache.logging.log4j.core.Logger,Suppression> suppressions=new IdentityHashMap<>();
 public final AtomicLong started=new AtomicLong(),ended=new AtomicLong();
 public ModTaskObserver(){super("ShangHanModTasks",null,null,true,Property.EMPTY_ARRAY);start();}
 public synchronized void attach(org.apache.logging.log4j.Logger api){
  if(api instanceof org.apache.logging.log4j.core.Logger core&&!attached.containsKey(core)){
   Level original=core.getLevel();boolean wasAdditive=core.isAdditive(),hadOwn=core.getName().equals(core.get().getName());attached.put(core,original);additive.put(core,wasAdditive);ownConfig.put(core,hadOwn);core.addAppender(this);core.setAdditive(hadOwn?wasAdditive:true);
   // Observe structured TRACE in memory without newly flooding ancestor file/console appenders.
   // Preserve explicit TRACE/ALL logging requested by the pack's existing configuration.
   var parent=core.get().getParent();
   if(parent!=null&&!original.isLessSpecificThan(Level.TRACE)&&parent.getFilter()==null){
    String loggerName=core.getName();
    var filter=new org.apache.logging.log4j.core.filter.AbstractFilter(){
     @Override public Result filter(LogEvent event){return event.getLevel()==Level.TRACE&&loggerName.equals(event.getLoggerName())?Result.DENY:Result.NEUTRAL;}
    };
    parent.addFilter(filter);suppressions.put(core,new Suppression(parent,filter));
   }
   core.setLevel(Level.TRACE);
  }
 }
 public void name(String id,String name){names.put(id,name);}
 public List<Task> current(){return active.values().stream().sorted(Comparator.comparingLong(Task::start)).toList();}
 public String label(Task task){return names.getOrDefault(task.id(),task.id())+" ["+task.id()+"] · "+task.step();}
 public List<String> samples(){return samples.stream().limit(12).toList();}
 @Override public void append(LogEvent event){
  try{
   String format=event.getMessage().getFormat();Object[] p=event.getMessage().getParameters();
   if(p==null||p.length==0)return;
   String id=String.valueOf(p[0]),thread=event.getThreadName(),kind="",step="";boolean begin=false,end=false;
   switch(format){
    case "Loading mod instance {} of type {}"->{kind="construct";step="初始化模组实例";begin=true;}
    case "Loaded mod instance {} of type {}"->{kind="construct";end=true;}
    case "Injecting Automatic event subscribers for {}"->{kind="listeners";step="初始化事件监听";begin=true;}
    case "Completed Automatic event subscribers for {}"->{kind="listeners";end=true;}
    case "Firing event for modid {} : {}"->{if(p.length<2)return;if(!important(p[1]))return;kind="event:"+System.identityHashCode(p[1]);step=eventStep(p[1]);begin=true;}
    case "Fired event for modid {} : {}"->{if(p.length<2)return;if(!important(p[1]))return;kind="event:"+System.identityHashCode(p[1]);end=true;}
    case "Failed to create mod instance. ModID: {}, class {}",
         "Failed to register automatic subscribers. ModID: {}, class {}"->{active.values().removeIf(t->t.id().equals(id)&&t.thread().equals(thread));return;}
    default->{return;}
   }
   String key=thread+":"+id+":"+kind;
   if(begin){
    active.put(key,new Task(key,id,step,event.getTimeMillis(),thread));started.incrementAndGet();
    if(samples.size()<40)samples.addLast(id+" · "+step+" · "+thread);
   }else if(end){active.remove(key);ended.incrementAndGet();}
  }catch(Throwable ignored){/* Observability may not break game startup. */}
 }
 private static boolean important(Object event){
  return switch(event.getClass().getSimpleName()){
   case "FMLConstructModEvent","FMLCommonSetupEvent","FMLClientSetupEvent","FMLLoadCompleteEvent",
        "InterModEnqueueEvent","InterModProcessEvent","RegisterEvent","RegisterGeometryLoaders",
        "RegisterAdditional","RegisterShadersEvent","RegisterKeyMappingsEvent","RegisterRenderers"->true;
   default->false;
  };
 }
 private static String eventStep(Object event){
  return switch(event.getClass().getSimpleName()){
   case "FMLConstructModEvent"->"构造阶段回调";
   case "FMLCommonSetupEvent"->"公共初始化";
   case "FMLClientSetupEvent"->"客户端初始化";
   case "FMLLoadCompleteEvent"->"完成初始化";
   case "InterModEnqueueEvent"->"模组通信入队";
   case "InterModProcessEvent"->"处理模组通信";
   case "RegisterEvent"->"注册游戏内容";
   case "RegisterGeometryLoaders"->"注册模型加载器";
   case "RegisterAdditional"->"注册附加模型";
   case "RegisterShadersEvent"->"注册着色器";
   case "RegisterKeyMappingsEvent"->"注册按键";
   case "RegisterRenderers"->"注册渲染器";
   default->"处理模组事件";
  };
 }
 @Override public synchronized void close(){
  for(var entry:attached.entrySet()){
   var core=entry.getKey();core.removeAppender(this);
   if(!ownConfig.get(core)&&core.get().getAppenders().isEmpty()&&core.get().getFilter()==null){core.getContext().getConfiguration().removeLogger(core.getName());core.getContext().updateLoggers();}
   else if(ownConfig.get(core))core.setAdditive(additive.get(core));
   core.setLevel(entry.getValue());
  }
  for(var entry:suppressions.values())entry.parent().removeFilter(entry.filter());suppressions.clear();
  attached.clear();additive.clear();ownConfig.clear();active.clear();stop();
 }
}

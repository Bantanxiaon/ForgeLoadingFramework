import shanghan.early.*;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.ParameterizedMessage;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class ProgressTaskSmoke {
 static void emit(ModTaskObserver o,String thread,String format,String id){o.append(Log4jLogEvent.newBuilder().setThreadName(thread).setMessage(new ParameterizedMessage(format,id,"ExampleClass")).build());}
 public static void main(String[] args)throws Exception{
  ModTaskObserver o=new ModTaskObserver();
  try{
   o.name("a","Mod A");emit(o,"worker1","Loading mod instance {} of type {}","a");emit(o,"worker2","Loading mod instance {} of type {}","b");
   if(o.current().size()!=2||!o.label(o.current().stream().filter(t->t.id().equals("a")).findFirst().orElseThrow()).contains("Mod A [a]"))throw new AssertionError("Current parallel tasks");
   emit(o,"worker1","Loaded mod instance {} of type {}","a");if(o.current().size()!=1||!o.current().get(0).id().equals("b"))throw new AssertionError("Completed task retained");
   emit(o,"worker2","Failed to create mod instance. ModID: {}, class {}","b");if(!o.current().isEmpty())throw new AssertionError("Failed task retained");
  }finally{o.close();}
  var logger=(org.apache.logging.log4j.core.Logger)org.apache.logging.log4j.LogManager.getLogger("smoke.tasks");
  logger.setLevel(org.apache.logging.log4j.Level.INFO);var root=logger.getContext().getConfiguration().getRootLogger();
  java.util.concurrent.atomic.AtomicInteger trace=new java.util.concurrent.atomic.AtomicInteger(),info=new java.util.concurrent.atomic.AtomicInteger();
  var sink=new org.apache.logging.log4j.core.appender.AbstractAppender("task-smoke-sink",null,null,true,org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY){
   public void append(org.apache.logging.log4j.core.LogEvent e){if(e.getLevel()==org.apache.logging.log4j.Level.TRACE)trace.incrementAndGet();if(e.getLevel()==org.apache.logging.log4j.Level.INFO)info.incrementAndGet();}
  };sink.start();root.addAppender(sink,null,null);
  ModTaskObserver attached=new ModTaskObserver();attached.attach(logger);
  try{logger.trace("Loading mod instance {} of type {}","c","ExampleClass");if(attached.current().size()!=1)throw new AssertionError("In-memory TRACE not observed");logger.info("Preserve normal logging");Thread.sleep(100);if(trace.get()!=0||info.get()!=1)throw new AssertionError("TRACE forwarding counts trace="+trace.get()+" info="+info.get()+" parentFilter="+root.getFilter());}
  finally{attached.close();root.removeAppender(sink.getName());sink.stop();}
  if(logger.getLevel()!=org.apache.logging.log4j.Level.INFO||root.getFilter()!=null)throw new AssertionError("Logger state not restored");
  LoadingProgress progress=new LoadingProgress();double before=progress.snapshot().overall();Thread.sleep(100);if(progress.snapshot().overall()!=before)throw new AssertionError("Timer-based fake progress");
  progress.mods(428);progress.resource(.25f);if(progress.snapshot().stageFraction()!=.25||progress.snapshot().totalMods()!=428)throw new AssertionError("Actual state");
  progress.complete();if(progress.snapshot().accuracy()!=ProgressProvider.Accuracy.COMPLETE)throw new AssertionError("Completion");
  boolean rejected=false;try{RuntimeProfile.requireClient(new ByteArrayInputStream("{\"version\":\"different\",\"minecraft\":\"1.20.1\"}".getBytes(StandardCharsets.UTF_8)));}catch(IllegalStateException expected){rejected=true;}if(!rejected)throw new AssertionError("Mixed version pair accepted");
  System.out.println("PASS current parallel Mod entries, exact end removal, failure removal, no timer progress, published resource fraction, complete mixed-JAR guard and in-memory TRACE with normal logging restored.");
 }
}

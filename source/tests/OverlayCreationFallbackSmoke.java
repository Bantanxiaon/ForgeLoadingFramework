import shanghan.early.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
public final class OverlayCreationFallbackSmoke {
 static int mode,stockCalls;
 static final class Provider extends ShangHanProvider {
  boolean released;
  Provider(Path root)throws Exception {
   media=new MediaSession(root,new Settings());
   overlayFactory=OverlayCreationFallbackSmoke.class.getMethod("custom",Supplier.class,Supplier.class,Consumer.class,MediaSession.class,VideoRenderer.class);
   vanillaFactory=OverlayCreationFallbackSmoke.class.getMethod("stock",Supplier.class,Supplier.class,Consumer.class,boolean.class);
  }
  protected void stopMediaForStock(){released=true;media.close();media.finishLogging();}
  void done(){media.close();media.finishLogging();}
 }
 public static Supplier<Object> custom(Supplier<?> mc,Supplier<?> reload,Consumer<Optional<Throwable>> finish,MediaSession media,VideoRenderer renderer) {
  return ()->{if(mode==2)finish.accept(Optional.empty());if(mode==3)return null;throw new IllegalStateException("construction failure");};
 }
 public static Supplier<Object> stock(Supplier<?> mc,Supplier<?> reload,Consumer<Optional<Throwable>> finish,boolean fade){
  stockCalls++;return ()->"stock";
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);
  for(int test=1;test<=3;test++){
   mode=test;stockCalls=0;AtomicInteger completed=new AtomicInteger();
   Provider provider=new Provider(root.resolve("case"+test));
   try{
    Supplier<Object> result=provider.loadingOverlay(()->null,()->null,event->completed.incrementAndGet(),true);
    if(test==2){
     try{result.get();throw new AssertionError("Completion callback exception was swallowed");}
     catch(IllegalStateException expected){}
     if(stockCalls!=0||completed.get()!=1||provider.released)throw new AssertionError("Completion callback was replayed or failure path altered");
    }else{
     if(!"stock".equals(result.get())||stockCalls!=1||!provider.released||completed.get()!=0)throw new AssertionError("Lazy creation fallback did not release media exactly once");
    }
   }finally{provider.done();}
  }
  System.out.println("PASS lazy subclass failure and null result fall back after media cleanup; completion-started errors propagate without duplicate callback");
 }
}

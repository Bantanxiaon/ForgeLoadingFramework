package shanghan.early;
/** UI consumes immutable snapshots; providers must be nonblocking and failure-isolated. */
public interface ProgressProvider {
 enum Accuracy { ESTIMATE, STAGE_FRACTION, COMPLETE }
 record Snapshot(double overall,String stage,Accuracy accuracy,double stageFraction,int discoveredMods,int totalMods,String sourceLabel){}
 Snapshot snapshot();
}

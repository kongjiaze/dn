package cn.kongj.blackstone;

/** Accepted touch time is separate from a successful completion callback. */
final class StrokeOutcome {
 final boolean accepted,completed,uncertain;final long elapsedMs;
 StrokeOutcome(boolean accepted,boolean completed,boolean uncertain,long duration,long elapsed){
  this.accepted=accepted;this.completed=completed;this.uncertain=uncertain;
  elapsedMs=accepted?(completed?duration:Math.max(0,Math.min(duration,elapsed))):0;
 }
}

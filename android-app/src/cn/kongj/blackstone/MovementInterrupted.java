package cn.kongj.blackstone;

/** A failed movement can still have moved the character; do not replay that part. */
final class MovementInterrupted extends Exception {
 final int movedMs;
 MovementInterrupted(int requested,long moved,String reason){super(reason);movedMs=(int)Math.max(0,Math.min(requested,moved));}
}

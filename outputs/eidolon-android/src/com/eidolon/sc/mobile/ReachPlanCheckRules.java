package com.eidolon.sc.mobile;

/** Pure local wake policy, also used by offline regression checks. */
final class ReachPlanCheckRules {
    static boolean active(String state){return "PENDING".equals(state)||"RUNNING".equals(state);}
    static boolean overdue(long wake,long now){return wake>0&&wake<=now;}
    static long next(ReachConfig config,long now,long due){
        long at=Math.min(ReachPlanRules.nextMorning(config,now),now+config.integer("backgroundMinutes")*60000L);
        if(due>0)at=Math.min(at,Math.max(now+30000L,due));
        return at;
    }
    private ReachPlanCheckRules(){}
}

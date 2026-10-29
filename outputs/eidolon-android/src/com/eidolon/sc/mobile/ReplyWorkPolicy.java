package com.eidolon.sc.mobile;
/** Pure timing boundaries for the bounded user-started background guard. */
final class ReplyWorkPolicy {
    static String localTestAction(String state,long due,long now){
        if(!state.equals("waiting"))return "drop";
        return now>=due?"deliver":"wait";
    }
    static String action(String state,boolean test,long sent,long due,boolean immediate,long now){
        if(test||state.equals("cancelled")||state.equals("failed"))return "drop";
        if(state.equals("collecting"))return now>=sent&&now-sent>=4000?"generate":"wait";
        if(state.equals("waiting")){if(immediate||due<=now)return "deliver";if(due-now>ReplyWorkService.LIMIT)return "drop";}
        return "wait";
    }
    private ReplyWorkPolicy(){}
}

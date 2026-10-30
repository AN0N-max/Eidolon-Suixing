package com.eidolon.sc.mobile;

import java.io.IOException;
import java.util.Collections;
import org.json.JSONObject;

/** Bounded output budget and retry policy, shared by manual and automatic schedules. */
final class ScheduleGeneration {
    static final int INITIAL_TOKENS=4096, RETRY_TOKENS=8192;
    interface Transport { String chat(JSONObject request)throws Exception; }
    interface Guard { void check()throws Exception; }

    static JSONObject generate(String model,String instruction,JSONObject facts,Transport transport,Guard guard)throws Exception{
        for(int attempt=0;attempt<2;attempt++){
            guard.check();
            String compact="只返回一个完整 JSON 对象，不要 Markdown、说明、复述资料，除了已说明的可选continuity不要额外字段。activity 每项最多 24 字，place 最多 16 字，twists 每条最多 40 字。";
            if(attempt==1)compact+="上次输出被长度上限截断；本次只排三段简短安排，twists 用空数组，保持完整结构。";
            JSONObject request=ModelClient.request(model,instruction+"\n"+compact,Collections.emptyList(),facts.toString())
                .put("max_tokens",attempt==0?INITIAL_TOKENS:RETRY_TOKENS);
            try{
                String raw=transport.chat(request);
                guard.check();
                return ScheduleData.parse(raw);
            }catch(ModelClient.OutputLimitException limit){
                // Never parse/save partial text, even if its prefix resembles valid JSON.
                guard.check();
                if(attempt==1)throw new IOException("行程输出仍被模型服务截断（已扩大输出额度重试一次）。请在“模型与接口 → 后台与判断模型”换用非思考的后台模型，或检查服务商的输出上限后重新安排。",limit);
            }
        }
        throw new AssertionError("Unreachable schedule attempt");
    }
}

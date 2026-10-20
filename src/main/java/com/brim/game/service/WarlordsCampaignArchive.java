package com.brim.game.service;

import com.brim.game.game.warlords.WarlordsGame.SavedCampaign;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** A campaign row is both its durable checkpoint and its owner-generation fence. */
@Service
public class WarlordsCampaignArchive {
    public record Lease(long generation,SavedCampaign saved) {}
    public record Summary(int chapter,String phase,long updatedAt) {}
    private final WarlordsDatabase database;
    private final ObjectMapper json;
    public WarlordsCampaignArchive(WarlordsDatabase database,ObjectMapper json){this.database=database;this.json=json;}
    private String key(String player){return "campaign:"+player;}
    public Lease acquire(String player,String room) {
        return database.update(key(player),HashMap::new,data->{
            long generation=data.get("generation") instanceof Number n?n.longValue()+1:1;
            SavedCampaign saved=data.get("checkpoint")==null?null:json.convertValue(data.get("checkpoint"),SavedCampaign.class);
            data.put("generation",generation);data.put("roomId",room);
            return new Lease(generation,saved);
        });
    }
    public <T> T transact(String player,String room,long generation,Supplier<T> action,Supplier<SavedCampaign> checkpoint) {
        return database.update(key(player),HashMap::new,data->{
            if(!room.equals(data.get("roomId"))||!(data.get("generation") instanceof Number owner)||owner.longValue()!=generation)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chiến dịch đã được mở ở phòng khác. Hãy về bản đồ và vào lại.");
            if(data.get("checkpoint") instanceof Map<?,?> stored&&stored.get("revision") instanceof Number revision
                    &&revision.longValue()!=checkpoint.get().revision())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Lượt đã lưu thay đổi. Đang đồng bộ lại chiến dịch.");
            T result=action.get();
            data.put("checkpoint",checkpoint.get());data.put("updatedAt",System.currentTimeMillis());
            return result;
        });
    }
    public SavedCampaign latest(String player,String room,long generation) {
        var data=database.read(key(player));
        if(data==null||!room.equals(data.get("roomId"))||!(data.get("generation") instanceof Number owner)||owner.longValue()!=generation)
            throw new IllegalStateException("Campaign ownership changed.");
        return data.get("checkpoint")==null?null:json.convertValue(data.get("checkpoint"),SavedCampaign.class);
    }
    public Summary summary(String player) {
        var data=database.read(key(player));
        if(data==null||!(data.get("checkpoint") instanceof Map<?,?> saved))return null;
        String phase=String.valueOf(saved.get("phase"));
        if(!Set.of("BATTLE","RESULT").contains(phase))return null;
        int chapter=saved.get("chapter") instanceof Number n?n.intValue():0;
        return new Summary(chapter,phase,data.get("updatedAt") instanceof Number n?n.longValue():0);
    }
}

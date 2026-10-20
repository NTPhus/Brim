package com.brim.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable documents share the application's pooled datasource and transaction boundary. */
@Component
public class WarlordsDatabase {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    public WarlordsDatabase(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.json=json;transactions=new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE IF NOT EXISTS warlords_documents (id VARCHAR(160) PRIMARY KEY, payload TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS brim_accounts (id VARCHAR(64) PRIMARY KEY, login_name VARCHAR(32) NOT NULL UNIQUE, display_name VARCHAR(32) NOT NULL, password_hash VARCHAR(100) NOT NULL)");
    }
    public <T> T transaction(Supplier<T> action) {return transactions.execute(status->action.get());}
    public Map<String,Object> read(String key) {
        var rows=jdbc.query("SELECT payload FROM warlords_documents WHERE id=?",(rs,n)->decode(rs.getString(1)),key);
        return rows.isEmpty()?null:rows.get(0);
    }
    public <T> T update(String key,Supplier<Map<String,Object>> initial,Function<Map<String,Object>,T> action) {
        for(int attempt=0;;attempt++) {
            try {return transaction(()->{
                var rows=jdbc.query("SELECT payload FROM warlords_documents WHERE id=? FOR UPDATE",(rs,n)->decode(rs.getString(1)),key);
                Map<String,Object> data=rows.isEmpty()?new HashMap<>(initial.get()):rows.get(0);
                if(rows.isEmpty())jdbc.update("INSERT INTO warlords_documents(id,payload) VALUES (?,?)",key,encode(data));
                T result=action.apply(data);
                jdbc.update("UPDATE warlords_documents SET payload=? WHERE id=?",encode(data),key);
                return result;
            });} catch(DuplicateKeyException race) {if(attempt>=2)throw race;}
        }
    }
    private Map<String,Object> decode(String raw) {
        try{return json.readValue(raw,new TypeReference<HashMap<String,Object>>(){});}
        catch(java.io.IOException invalid){throw new IllegalStateException("Dữ liệu Warlords không đọc được.",invalid);}
    }
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}
        catch(java.io.IOException invalid){throw new IllegalStateException("Không thể lưu Warlords.",invalid);}
    }
}

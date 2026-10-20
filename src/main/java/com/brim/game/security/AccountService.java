package com.brim.game.security;

import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.brim.game.service.WarlordsDatabase;

@Service
public class AccountService {
    public record Account(String id,String username) {}
    private final JdbcTemplate jdbc;
    private final WarlordsDatabase database;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    // Perform the same expensive comparison for unknown accounts.
    private final String dummyHash=passwords.encode(UUID.randomUUID().toString());
    public AccountService(JdbcTemplate jdbc,WarlordsDatabase database){this.jdbc=jdbc;this.database=database;}
    private String login(String name) {
        if(name==null||!name.matches("[a-zA-Z0-9_]{3,32}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên đăng nhập gồm 3–32 chữ, số hoặc dấu gạch dưới.");
        return name.toLowerCase(Locale.ROOT);
    }
    private void password(String value) {
        if(value==null||value.length()<8||value.length()>72||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu cần 8–72 ký tự và tối đa 72 byte UTF-8.");
    }
    public Account register(String username,String password,GuestSession guest) {
        String normalized=login(username);password(password);
        if(guest!=null&&guest.account())throw new ResponseStatusException(HttpStatus.CONFLICT,"Bạn đã đăng nhập tài khoản.");
        String id=guest==null?UUID.randomUUID().toString():guest.id();
        String display=guest==null?username:guest.username();String hash=passwords.encode(password);
        try {return database.transaction(()->{
            jdbc.update("INSERT INTO brim_accounts(id,login_name,display_name,password_hash) VALUES (?,?,?,?)",id,normalized,display,hash);
            return new Account(id,display);
        });} catch(DuplicateKeyException duplicate){throw new ResponseStatusException(HttpStatus.CONFLICT,"Tên đăng nhập đã tồn tại.");}
    }
    public Account authenticate(String username,String password) {
        String normalized=login(username);password(password);
        var rows=jdbc.query("SELECT id,display_name,password_hash FROM brim_accounts WHERE login_name=?",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)},normalized);
        String[] row=rows.isEmpty()?null:rows.get(0);
        boolean matches=passwords.matches(password,row==null?dummyHash:row[2]);
        if(row==null||!matches)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Tên đăng nhập hoặc mật khẩu không đúng.");
        return new Account(row[0],row[1]);
    }
    public void rename(String id,String name){jdbc.update("UPDATE brim_accounts SET display_name=? WHERE id=?",name,id);}
}

package com.westflow.system.log.mapper;

import com.westflow.system.log.model.LoginLogRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 登录日志快照存储，便于运营端按条件检索与回看。
 */
@Component
public class LoginLogMapper {

    private final List<LoginLogRecord> storage = new ArrayList<>();

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized void insert(LoginLogRecord record) {
        storage.add(record);
    }

    public synchronized List<LoginLogRecord> selectAll() {
        return new ArrayList<>(storage);
    }
}

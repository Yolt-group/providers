package com.yolt.providers.web.threadinfo;

import lombok.*;

import java.util.List;

@Value
public class ThreadInfo {

    String threadName;
    String state;
    List<String> stackTraceElements;
}

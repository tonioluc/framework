package com.example.utils;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    public ModelView() {
    }

    private String viewName;
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    private Map<String, Object> data = new HashMap<>();

    public ModelView(String viewName) {
        this.viewName = viewName;
    }

    public String getViewName() {
        return viewName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void addData(String name, Object value) {
        this.data.put(name, value);
    }
}

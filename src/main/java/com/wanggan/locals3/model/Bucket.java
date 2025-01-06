package com.wanggan.locals3.model;

public class Bucket {
    private String name;
    private String creationDate;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public static Bucket of(String name, String creationDate) {
        Bucket bucket = new Bucket();
        bucket.setName(name);
        bucket.setCreationDate(creationDate);
        return bucket;
    }
}

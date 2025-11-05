package com.devoxx;


public class Item {

    private String uuid;
    private String companyId;
    private String name;
    private double price;
    private int quantity;

    public Item() {
    }

    public Item(String uuid, String companyId, String name, double price, int quantity) {
        this.uuid = uuid;
        this.companyId = companyId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "com.devoxx.Item{" +
                "uuid='" + uuid + '\'' +
                ", companyId='" + companyId + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                '}';
    }
}
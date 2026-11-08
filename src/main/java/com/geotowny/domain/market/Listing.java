package com.geotowny.domain.market;

import com.geotowny.domain.money.Money;

public class Listing {

    private long id;
    private String sellerUuid;
    private String sellerName;
    private String itemId;
    private String displayName;
    private String itemData;
    private int quantity;
    private Money unitPrice = Money.ZERO;
    private long listedAt;

    public Listing() {
    }

    public Listing(long id, String sellerUuid, String sellerName, String itemId,
                   String displayName, String itemData, int quantity, Money unitPrice, long listedAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemId = itemId;
        this.displayName = displayName;
        this.itemData = itemData;
        this.quantity = quantity;
        this.unitPrice = unitPrice == null ? Money.ZERO : unitPrice;
        this.listedAt = listedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSellerUuid() {
        return sellerUuid;
    }

    public void setSellerUuid(String sellerUuid) {
        this.sellerUuid = sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }

    public Money getUnitPrice() {
        return unitPrice == null ? Money.ZERO : unitPrice;
    }

    public void setUnitPrice(Money unitPrice) {
        this.unitPrice = unitPrice == null ? Money.ZERO : unitPrice;
    }

    public long getListedAt() {
        return listedAt;
    }

    public void setListedAt(long listedAt) {
        this.listedAt = listedAt;
    }

    public Money totalPrice() {
        return Money.ofCents(Math.multiplyExact(getUnitPrice().cents(), (long) quantity));
    }
}
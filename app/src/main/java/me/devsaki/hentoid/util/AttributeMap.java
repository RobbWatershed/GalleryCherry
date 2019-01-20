package me.devsaki.hentoid.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;

/**
 * Created by Shiro on 1/11/2016.
 * Builds AttributeMaps
 */
public class AttributeMap extends HashMap<AttributeType, List<Attribute>> {

    public void add(Attribute attributeItem) {
        if (null == attributeItem) return;

        List<Attribute> list;
        AttributeType type = attributeItem.getType();

        if (containsKey(type)) {
            list = get(type);
        } else {
            list = new ArrayList<>();
            put(type, list);
        }
        list.add(attributeItem);
    }

    public void add(List<Attribute> attributeList) {
        if (null == attributeList) return;

        for (Attribute item : attributeList) {
            add(item);
        }
    }
}

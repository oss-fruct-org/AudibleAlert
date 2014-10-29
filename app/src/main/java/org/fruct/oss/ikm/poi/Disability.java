package org.fruct.oss.ikm.poi;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Root(strict = false)
public class Disability{

    @Element
    private String name;

    public String getName() {
        return name;
    }


    @ElementList(entry = "category", inline= true, required = false)
    private List<String> categories;

    public List<String> getCategories(){
        return categories;
    }
/*
        public Disability(String name,List<String> categories){
            this.name = name;
            this.categories = categories;
        }*/

}
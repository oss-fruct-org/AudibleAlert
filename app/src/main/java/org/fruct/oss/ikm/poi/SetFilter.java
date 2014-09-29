package org.fruct.oss.ikm.poi;

import java.util.HashSet;
import java.util.Set;


public class SetFilter implements Filter {
    String name;
    private boolean active = true;
    private Set<Filter> filters = new HashSet<Filter>();

    public SetFilter(String filterName, Set<Filter> filters){
        this.name = filterName + " ( ";


        for(Filter flt : filters){
            this.filters.add(flt);
            if(!filterName.equalsIgnoreCase("All"))
                name += flt.getString() + " ";
        }

        if(filterName.equalsIgnoreCase("All"))
            name += "... ";
        name += ")";
    }

    @Override
    public boolean accepts(PointDesc point) {
        boolean accepts = false;
        for(Filter flt : filters)
            if(flt.accepts(point))
                accepts = true;

        return accepts;
    }

    @Override
    public String getString() {
        return name;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}

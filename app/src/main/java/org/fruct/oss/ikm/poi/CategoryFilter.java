package org.fruct.oss.ikm.poi;

public class CategoryFilter implements Filter {
	private String category;
	private boolean active = true;
	private String name;



    private String id;

	public CategoryFilter(String category, String name, String id) {
		this.category = category;
		this.name = name;
        this.id = id;
	}
	
	@Override
	public boolean accepts(PointDesc point) {
		return category.equals(point.getCategory());
	}

	@Override
	public String getString() {
		return name;
	}

    @Override
    public String getName() {
        return null;
    }

    @Override
	public boolean isActive() {
		return active;
	}
	
	@Override
	public void setActive(boolean active) {
		this.active = active;
	}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}

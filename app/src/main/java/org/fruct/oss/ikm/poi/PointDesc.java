package org.fruct.oss.ikm.poi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Patterns;

import org.fruct.oss.aa.CategoriesManager;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.Serializable;
import java.util.regex.Matcher;

public class PointDesc implements Serializable, Parcelable {
	private static final long serialVersionUID = 1L;
	
	private int latE6, lonE6;
	private String name;
	
	private GeoPoint geoPoint;
	
	private String category;
	private String desc;

    private String cat_id;

    private int rating = 1; // default value

    private String image;
	
	private static int nextId = 0;
	private int internalId;

	private RelativeDirection dir;
	private int distance;

	private transient boolean isDescriptionUrl = false;

	public PointDesc(String name, int latE6, int lonE6) {
		this.name = name;
		this.latE6 = latE6;
		this.lonE6 = lonE6;
		
		internalId = nextId++;
	}
	
	public PointDesc setCategory(String cat) {
		this.category = CategoriesManager.getName(cat);
		return this;
	}

	public boolean isDescriptionUrl() {
		return isDescriptionUrl;
	}

	public PointDesc setDescription(String d) {
		if (d == null)
            d = "";
        this.desc = "";
        parseDescription(d);

		return this;
	}

    /**
     * @param desc
     *
     * Excepcting this/same JSON:
     * {"rating" : "10"; "description": "the best point ever"}
     */
    private void parseDescription(String desc){
        String d, r;
        try {
            JSONObject root = new JSONObject(desc);
            r = root.getString("rating");
            this.rating = Integer.parseInt(r);
            d = root.getString("description");
            this.desc = d;
        }catch (JSONException e) {
           // e.printStackTrace();
        }catch(Exception e){
           // e.printStackTrace();
        }

    }
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return desc;
	}
	
	public String getCategory() {
		return category;
	}
	
	public GeoPoint toPoint() {
		return geoPoint == null ? geoPoint = new GeoPoint(latE6, lonE6)
								: geoPoint;
	}
	

	public RelativeDirection getRelativeDirection() {
		return dir;
	}
	
	public void setRelativeDirection(RelativeDirection dir) {
		this.dir = dir;
	}
	
	
	public int getDistance() {
		return distance;
	}
	
	public void setDistance(int distance) {
		this.distance = distance;
	}

    public PointDesc setCat_id(String cat_id) {this.cat_id = cat_id; return this;}

    public String getCat_id(){return this.cat_id;}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + internalId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PointDesc other = (PointDesc) obj;
		return internalId == other.internalId;
	}

    public int getRating(){
        return rating;
    }

	@Override
	public String toString() {
		return "PointDesc{" +
				"latE6=" + latE6 +
				", lonE6=" + lonE6 +
				", name='" + name + '\'' +
				", category='" + category + '\'' +
				", desc='" + desc + '\'' +
				'}';
	}

	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(latE6);
		dest.writeInt(lonE6);
		dest.writeString(name);
		dest.writeString(desc);
		dest.writeString(category);
		dest.writeInt(internalId);
		
		dest.writeInt(dir != null ? dir.ordinal() : -1);
		
		dest.writeInt(distance);
	}
	
	public static final Creator<PointDesc> CREATOR = new Creator<PointDesc>() {
		@Override
		public PointDesc[] newArray(int size) {
			return new PointDesc[size];
		}
	
		@Override
		public PointDesc createFromParcel(Parcel source) {
			int lat = source.readInt();
			int lon = source.readInt();
			String name = source.readString();
			
			PointDesc ret = new PointDesc(name, lat, lon)
				.setDescription(source.readString())
				.setCategory(source.readString());
			
			ret.internalId = source.readInt();
			
			int dirOrd = source.readInt();
			if (dirOrd >= 0)
				ret.dir = RelativeDirection.values()[dirOrd];
			
			ret.distance = source.readInt();
			return ret;
		}
	};
}

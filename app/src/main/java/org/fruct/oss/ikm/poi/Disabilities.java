package org.fruct.oss.ikm.poi;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
@Root
public class Disabilities {

   @ElementList(name = "disabilities", entry = "disability", type = Disability.class, inline = true)
   private List<Disability> disabilities;

   public List<Disability> getDisabilities(){
       return disabilities;
   }



    public static Disabilities createFromStream(InputStream stream) throws IOException {
        Serializer serializer = new Persister();
        try {
            return serializer.read(Disabilities.class, stream);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}

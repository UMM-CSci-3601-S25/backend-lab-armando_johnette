package userTodos;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})

public class usertodos {
  @ObjectId @Id

  @SuppressWarnings({"MemberName"})

  public String _id;
  public String body;
  public String status;
  public int owner;
  public String company;
  public String $oid;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof usertodos)) {
      return false;
    }
    usertodos other = (usertodos) obj;
    return _id.equals(other._id);
  }



}

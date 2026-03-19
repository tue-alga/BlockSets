package ilp.solvers.mosaicsets;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that groups the elements with its corresponding set elements.
 * 
 * @param <T> Defines the type of the set element
 */
public class ElementWithSets<T extends Comparable<T>>
    implements Comparable<ElementWithSets<T>> {
  // The element itself
  T element;

  // List of sets in which the element is present
  List<Integer> setIDs;

  public ElementWithSets(T element) {
    this.element = element;
    setIDs = new ArrayList<Integer>();
  }

  @Override
  public int compareTo(ElementWithSets<T> o) {
    if (this.setIDs.size() < o.setIDs.size()) {
      return -1;
    } else if (this.setIDs.size() > o.setIDs.size()) {
      return 1;
    } else {
      for (int i = 0; i < this.setIDs.size(); i++) {
        if (this.setIDs.get(i).compareTo(o.setIDs.get(i)) < 0) {
          return -1;
        } else if (this.setIDs.get(i).compareTo(o.setIDs.get(i)) > 0) {
          return 1;
        }
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    String s = "" + element + ":";
    for (Integer i : setIDs)
      s += ("" + i + ",");
    return s;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ElementWithSets) {
      @SuppressWarnings("unchecked")
      int isEqual = this.compareTo((ElementWithSets<T>) o);
      return (isEqual == 0);
    }
    return false;
  }
}

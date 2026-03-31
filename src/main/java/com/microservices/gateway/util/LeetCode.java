package com.microservices.gateway.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

public class LeetCode {
    public static List<Row> rows = new ArrayList<>();


    public static void main(String args[]) {
        rows.add(new Row("Alice", 50, "SF"));
        rows.add(new Row("James", 60, "NY"));


        for (int i = 0; i < rows.size(); i++) {
            invalidTransaction(rows.get(i));
        }


    }


    public static List<Row> invalidTransaction(Row row) {
        List<Row> returnRown = null;
        for (Row rown : rows) {
            System.out.println(rown.getName());
            // if there are two rows with different cities and time between
            // is less than 60
            if(row.getCity() != rown.getCity()){
                if((rown.getTime() - row.getTime())> 10 ){

                    returnRown= List.of(
                            new Row(rown.getName(), rown.getTime(), rown.getCity()),
                    new Row(row.getName(), row.getTime(), row.getCity())
                    );

                    System.out.println(returnRown.toString());

                    return returnRown;

                }

            }

            return returnRown;

        }


        return returnRown;

    }

}

@Data
@AllArgsConstructor
@ToString
class Row {
    private String name;
    private int time;
    private String city;
}

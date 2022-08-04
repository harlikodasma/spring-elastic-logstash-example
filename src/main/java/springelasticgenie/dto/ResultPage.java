package springelasticgenie.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.List;

@Getter
@Setter
public class ResultPage<T> {

    private List<T> content;
    private Integer size;
    private Integer offset;
    private BigInteger totalElements;

}

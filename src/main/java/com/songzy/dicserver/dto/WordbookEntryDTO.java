package com.songzy.dicserver.dto;

import com.songzy.dicserver.model.PartOfSpeech;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WordbookEntryDTO {
    private String word;
    private String partOfSpeech;
    private String meaning;
    private LocalDateTime createTime;

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = PartOfSpeech.normalize(partOfSpeech);
    }

}

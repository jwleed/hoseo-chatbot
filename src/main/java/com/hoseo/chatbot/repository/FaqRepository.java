package com.hoseo.chatbot.repository;

import com.hoseo.chatbot.entity.FaqEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FaqRepository extends JpaRepository<FaqEntity, Long> {
    // 활성 FAQ만 sort_order 오름차순으로 조회합니다.
    @Query(value = "select * from faqs where is_active = true order by sort_order asc", nativeQuery = true)
    List<FaqEntity> findByIsActiveTrueOrderBySortOrderAsc();

    // 특정 카테고리의 활성 FAQ만 sort_order 오름차순으로 조회합니다.
    @Query(value = "select * from faqs where category = :category and is_active = true order by sort_order asc", nativeQuery = true)
    List<FaqEntity> findByCategoryAndIsActiveTrueOrderBySortOrderAsc(String category);

}
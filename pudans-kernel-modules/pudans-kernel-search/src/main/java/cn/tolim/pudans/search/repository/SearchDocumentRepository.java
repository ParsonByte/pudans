package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SearchDocumentRepository extends JpaRepository<SearchDocumentEntity, String>,
        SearchDocumentRepositoryCustom {

    Optional<SearchDocumentEntity> findByAppCodeAndIndexCodeAndDocumentId(String appCode, String indexCode,
                                                                         String documentId);

    boolean existsByAppCodeAndIndexCodeAndDocumentId(String appCode, String indexCode, String documentId);
}
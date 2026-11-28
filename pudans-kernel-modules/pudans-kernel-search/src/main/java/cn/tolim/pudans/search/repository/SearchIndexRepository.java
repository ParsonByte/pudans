package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.entity.SearchIndexEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchIndexRepository extends JpaRepository<SearchIndexEntity, String> {

    Optional<SearchIndexEntity> findByAppCodeAndIndexCode(String appCode, String indexCode);

    boolean existsByAppCodeAndIndexCode(String appCode, String indexCode);

    List<SearchIndexEntity> findAllByAppCodeOrderByIndexCodeAsc(String appCode);
}
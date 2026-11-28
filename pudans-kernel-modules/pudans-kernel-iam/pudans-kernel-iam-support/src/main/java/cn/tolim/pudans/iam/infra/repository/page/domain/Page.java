package cn.tolim.pudans.iam.infra.repository.page.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

@Schema(description = "分页结果")
public class Page<T> implements Serializable {
    private List<T> list;
    private Pagination pagination;

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    @Schema(description = "分页元数据")
    public static class Pagination implements Serializable {
        private Long total;
        private Integer totalPages;
        private Integer current;

        public Pagination() {
        }

        public Pagination(Long total, Integer totalPages, Integer current) {
            this.total = total;
            this.totalPages = totalPages;
            this.current = current;
        }

        public static PaginationBuilder builder() {
            return new PaginationBuilder();
        }

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Integer getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(Integer totalPages) {
            this.totalPages = totalPages;
        }

        public Integer getCurrent() {
            return current;
        }

        public void setCurrent(Integer current) {
            this.current = current;
        }

        public static class PaginationBuilder {
            private Long total;
            private Integer totalPages;
            private Integer current;

            public PaginationBuilder total(Long total) {
                this.total = total;
                return this;
            }

            public PaginationBuilder totalPages(Integer totalPages) {
                this.totalPages = totalPages;
                return this;
            }

            public PaginationBuilder current(Integer current) {
                this.current = current;
                return this;
            }

            public Pagination build() {
                return new Pagination(total, totalPages, current);
            }
        }
    }
}

import { PaginationProps } from '../../../../hooks/usePagination';
import './BlogPagination.scss';

interface BlogPaginationProps extends PaginationProps {}

export default function BlogPagination(props: BlogPaginationProps) {
  return (
    <nav className="blog-pagination justify-content-center d-flex">
      <ul className="pagination">
        <li className="page-item">
          <a
            className="page-link"
            aria-label="Previous"
            onClick={() => props.previousPage()}>
            <i className="ti-angle-left"></i>
          </a>
        </li>
        {[...new Array(props.pages)].map((x, i) => {
          const page = i + 1;
          let classNames = 'page-item';

          if (page === props.currentPage) {
            classNames += ' active';
          }

          return (
            <li
              key={page}
              className={classNames}
              onClick={() => props.togglePage(page)}>
              <a className="page-link">{page}</a>
            </li>
          );
        })}
        <li className="page-item">
          <a
            className="page-link"
            aria-label="Next"
            onClick={() => props.nextPage()}>
            <i className="ti-angle-right"></i>
          </a>
        </li>
      </ul>
    </nav>
  );
}

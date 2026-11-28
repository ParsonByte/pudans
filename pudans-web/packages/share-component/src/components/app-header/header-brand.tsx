type BrandProps = {
  brandName: string;
  href?: string;
  logoSrc: string;
};

export function Brand({ brandName, href = "/", logoSrc }: BrandProps) {
  return (
    <a aria-label={`${brandName} 首页`} className="pudans-app-header__brand" href={href}>
      <img alt="" className="pudans-app-header__brand-logo" src={logoSrc} />
      <span className="pudans-app-header__brand-name">{brandName}</span>
    </a>
  );
}

import type { AppHeaderMenu } from "./header-types";

type MegaMenuProps = {
  menu: AppHeaderMenu;
};

export function MegaMenu({ menu }: MegaMenuProps) {
  return (
    <div
      aria-label={typeof menu.label === "string" ? menu.label : undefined}
      className="pudans-app-header__mega"
      role="region"
    >
      <div className="pudans-app-header__mega-inner">
        <div className="pudans-app-header__mega-aside">
          <p className="pudans-app-header__mega-eyebrow">PUDANS</p>
          {menu.href ? (
            <a className="pudans-app-header__mega-title" href={menu.href}>
              {menu.label}
            </a>
          ) : (
            <h2 className="pudans-app-header__mega-title">{menu.label}</h2>
          )}
          {menu.description ? (
            <p className="pudans-app-header__mega-description">{menu.description}</p>
          ) : null}
        </div>
        <div className="pudans-app-header__mega-groups">
          {menu.groups?.map((group) => (
            <section className="pudans-app-header__mega-group" key={group.key}>
              {group.title ? (
                <h3 className="pudans-app-header__mega-group-title">{group.title}</h3>
              ) : null}
              <ul className="pudans-app-header__mega-list">
                {group.items.map((item) => (
                  <li key={item.key}>
                    <a className="pudans-app-header__mega-item" href={item.href}>
                      {item.icon ? (
                        <span className="pudans-app-header__mega-item-icon">{item.icon}</span>
                      ) : null}
                      <span className="pudans-app-header__mega-item-copy">
                        <span className="pudans-app-header__mega-item-label">{item.label}</span>
                        {item.description ? (
                          <span className="pudans-app-header__mega-item-description">
                            {item.description}
                          </span>
                        ) : null}
                      </span>
                    </a>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </div>
    </div>
  );
}

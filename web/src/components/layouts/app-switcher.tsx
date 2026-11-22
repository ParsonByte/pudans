import { Button, Popover } from "antd";

function AppsGridIcon() {
    return (
        <svg aria-hidden="true" fill="currentColor" height="1em" viewBox="0 0 12 12" width="1em">
            <path d="M0 0h3v3H0V0zm4.5 0h3v3h-3V0zM9 0h3v3H9V0zM0 4.5h3v3H0v-3zm4.5 0h3v3h-3v-3zM9 4.5h3v3H9v-3zM0 9h3v3H0V9zm4.5 0h3v3h-3V9zM9 9h3v3H9V9z" />
        </svg>
    );
}

export function AppSwitcher() {
    return (
        <Popover arrow={false} content={<div className="h-20 w-56" />} placement="bottomLeft" trigger="click">
            <Button aria-label="应用切换" icon={<AppsGridIcon />} type="text" />
        </Popover>
    );
}

import type { ThemeConfig } from 'antd';

/**
 * "Registered mail" palette: ink on paper, an envelope-grey desk, and a seal green that
 * marks verified/primary actions. Delivery states reuse the same three status colours
 * everywhere so a receipt reads the same in the list, the reading pane and the composer.
 */
export const palette = {
  ink: '#1B2A3D',
  inkSoft: '#4E5C6B',
  inkFaint: '#7D8896',
  envelope: '#ECEFF3',
  paper: '#FFFFFF',
  rule: '#D6DCE3',
  ruleSoft: '#E6EAEF',
  seal: '#0E6B62',
  sealTint: '#E2EFED',
  delivered: '#1E7F4F',
  pending: '#A86400',
  failed: '#B8352A',
} as const;

export const FONT_STACK =
  '"IBM Plex Sans", "PingFang SC", "Hiragino Sans GB", "Noto Sans SC", "Microsoft YaHei", system-ui, sans-serif';

export const theme: ThemeConfig = {
  token: {
    colorPrimary: palette.seal,
    colorLink: palette.seal,
    colorText: palette.ink,
    colorTextSecondary: palette.inkSoft,
    colorTextTertiary: palette.inkFaint,
    colorBgLayout: palette.envelope,
    colorBgContainer: palette.paper,
    colorBorder: palette.rule,
    colorBorderSecondary: palette.ruleSoft,
    colorSuccess: palette.delivered,
    colorWarning: palette.pending,
    colorError: palette.failed,
    fontFamily: FONT_STACK,
    fontSize: 14,
    borderRadius: 4,
    controlHeight: 34,
    boxShadow: 'none',
    boxShadowSecondary: '0 8px 24px rgba(27, 42, 61, 0.14)',
  },
  components: {
    Button: { primaryShadow: 'none', defaultShadow: 'none', dangerShadow: 'none', fontWeight: 500 },
    Input: { activeShadow: `0 0 0 2px ${palette.sealTint}` },
    Select: { optionSelectedBg: palette.sealTint },
    Menu: { itemSelectedBg: palette.paper, itemSelectedColor: palette.ink, itemBg: 'transparent' },
  },
};

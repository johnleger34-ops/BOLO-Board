# BOLO Board v1.5.3

Native Android police shift calendar and payroll estimator.

## v1.5.3 changes

- Corrected the rank-chip Java string that caused the prior GitHub Actions compile failure.
- Added automatic holiday premium pay for recognized federal holidays:
  - Day shift: 12 regular worked hours plus 12 holiday-premium hours.
  - Night shift: 12 regular worked hours plus 8 holiday-premium hours, ending at midnight.
  - Holiday premium is paid at the regular hourly rate.
  - Holiday premium does not count toward the 84-hour overtime threshold.
  - Holiday premium is pensionable for retirement calculations.
- Added Holiday Premium to payroll summaries, saved stubs, and shared pay-stub images.
- Federal and Louisiana withholding estimates are validated against five actual Alexis pay stubs and interpolate between observed gross-pay/tax points.
- Medicare remains 1.45% of gross pay.
- Fixed and strengthened Jump to Today; the current day is highlighted in gold.
- Kept J. Leger / A. Leger profile names and editable ranks.
- Kept permanent saved-stub history and newest-to-oldest selection.

## Payroll validation points

Observed actual stubs used by the estimator:

| Gross | Federal | Louisiana | Medicare |
|---:|---:|---:|---:|
| $1,615.38 | $10.19 | $27.77 | $23.42 |
| $1,644.23 | $13.65 | $28.66 | $23.84 |
| $2,066.62 | $57.75 | $40.97 | $29.97 |
| $2,307.69 | $93.17 | $49.14 | $33.46 |
| $2,461.54 | $109.83 | $53.43 | $35.69 |

These withholding amounts are employer-pattern estimates, not tax-return guarantees. Filing elections and agency payroll rules can change.

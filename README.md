# BOLO Board v1.5.4 — Payroll Precision Fix

This release replaces floating-point payroll math with decimal, cent-accurate calculations and migrates Alexis's previously rounded rates to the exact rates established by the official payroll stub.

Expected validation for 84 regular hours plus 12 OT hours:
- Regular wages: $1,701.92
- Overtime: $364.70
- Gross: $2,066.62
- Federal: $57.75
- Louisiana: $40.97
- Medicare: $29.97
- Retirement: $174.45
- Net: $1,667.90 when the saved deductions match the official stub

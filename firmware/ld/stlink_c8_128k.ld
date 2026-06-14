/* ST-Link / SWD direct upload — sem bootloader Maple
 * Código começa em 0x08000000 (flash completa, 128KB reais do CS32F103) */

MEMORY
{
  ram (rwx) : ORIGIN = 0x20000000, LENGTH = 20K
  rom (rx)  : ORIGIN = 0x08000000, LENGTH = 128K
}

REGION_ALIAS("REGION_TEXT",   rom);
REGION_ALIAS("REGION_DATA",   ram);
REGION_ALIAS("REGION_BSS",    ram);
REGION_ALIAS("REGION_RODATA", rom);

INCLUDE common.inc

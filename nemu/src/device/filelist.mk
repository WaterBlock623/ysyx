#***************************************************************************************
# Copyright (c) 2014-2024 Zihao Yu, Nanjing University
#
# NEMU is licensed under Mulan PSL v2.
# You can use this software according to the terms and conditions of the Mulan PSL v2.
# You may obtain a copy of Mulan PSL v2 at:
#          http://license.coscl.org.cn/MulanPSL2
#
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
# EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
# MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
#
# See the Mulan PSL v2 for more details.
#**************************************************************************************/

DIRS-y += $(NEMU_HOME)/src/device/io
SRCS-$(CONFIG_DEVICE) += $(NEMU_HOME)/src/device/device.c \
	$(NEMU_HOME)/src/device/alarm.c $(NEMU_HOME)/src/device/intr.c
SRCS-$(CONFIG_HAS_SERIAL) += $(NEMU_HOME)/src/device/serial.c
SRCS-$(CONFIG_HAS_TIMER) += $(NEMU_HOME)/src/device/timer.c
SRCS-$(CONFIG_HAS_KEYBOARD) += $(NEMU_HOME)/src/device/keyboard.c
SRCS-$(CONFIG_HAS_VGA) += $(NEMU_HOME)/src/device/vga.c
SRCS-$(CONFIG_HAS_AUDIO) += $(NEMU_HOME)/src/device/audio.c
SRCS-$(CONFIG_HAS_DISK) += $(NEMU_HOME)/src/device/disk.c
SRCS-$(CONFIG_HAS_SDCARD) += $(NEMU_HOME)/src/device/sdcard.c

SRCS-BLACKLIST-$(CONFIG_TARGET_AM) += $(NEMU_HOME)/src/device/alarm.c

ifdef CONFIG_DEVICE
ifndef CONFIG_TARGET_AM
LIBS += $(shell sdl2-config --libs)
endif
endif

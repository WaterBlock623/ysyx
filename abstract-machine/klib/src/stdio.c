#include <am.h>
#include <klib.h>
#include <klib-macros.h>
#include <stdarg.h>
#include <stdint.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

#define IS_DIGIT(c) ((c) >= '0' && (c) <= '9')

static int atoip(const char **s) {
    int i = 0;
    while (IS_DIGIT(**s))
        i = i * 10 + *((*s)++) - '0';
    return i;
}

#define F_ALT   1  // '#'
#define F_ZERO  2  // '0'
#define F_LEFT  4  // '-'
#define F_SPACE 8  // ' '
#define F_PLUS  16 // '+'

static int print_num(char *out, unsigned long long u, int base, int width, int flags, int neg) {
  char *start = out;
  char prefix[4];
  int prefix_len = 0;
  if (neg) {
    prefix[prefix_len++] = '-';
  } else if (flags & F_PLUS) {
    prefix[prefix_len++] = '+';
  } else if (flags & F_SPACE) {
    prefix[prefix_len++] = ' ';
  }

  if ((flags & F_ALT) && base == 16) {
    prefix[prefix_len++] = '0';
    prefix[prefix_len++] = 'x';
  }

  char tmp[100];
  int i = 0;
  if (u == 0) {
    tmp[i++] = '0';
  } else {
    const char *digits = "0123456789abcdef";
    while (u != 0) {
      int rem = u % base;
      tmp[i++] = digits[rem];
      u /= base;
    }
  }

  int num_len = i;
  int total_len = prefix_len + num_len;
  int padding = width > total_len ? width - total_len : 0;

  if (!(flags & F_LEFT) && !(flags & F_ZERO)) {
    while (padding-- > 0) *out++ = ' ';
  }
  for (int k = 0; k < prefix_len; k++) {
    *out++ = prefix[k];
  }
  if (!(flags & F_LEFT) && (flags & F_ZERO)) {
    while (padding-- > 0) *out++ = '0';
  }
  while (i-- > 0) {
    *out++ = tmp[i];
  }
  if (flags & F_LEFT) {
    while (padding-- > 0) *out++ = ' ';
  }

  return out - start;
}

static void parse_arg(const char **fmt, va_list *ap, 
                      uint32_t *flags, int *width, int *precision,
                      int *long_mod) {
  if (**fmt != '%')
    panic("Invalid fmt");
  (*fmt)++; 

  char flags_chars[] = {'#', '0', '-', ' ', '+'};
  while (1) {
    int i;
    for (i = 0; i < sizeof(flags_chars); i++) {
      if (**fmt == flags_chars[i]) {
        *flags |= (1u << i);
        (*fmt)++;
        break;
      }
    }
    if (i == sizeof(flags_chars))
      break;
  }

  if (**fmt == '*') {
    *width = va_arg(*ap, int);
    (*fmt)++;
    if (*width < 0) {
      *width = -*width;
      *flags |= F_LEFT;
    }
  } else if (IS_DIGIT(**fmt)) {
    *width = atoip(fmt);
  }

  if (**fmt == '.') {
    (*fmt)++;
    if (**fmt == '*') {
      *precision = va_arg(*ap, int);
      (*fmt)++;
    } else if (IS_DIGIT(**fmt)) {
      *precision = atoip(fmt);
    } else {
      *precision = 0;
    }
  }

  int is_mod = 0;
  do {
    is_mod = 0;
    if (**fmt == 'l') {
      (*long_mod)++;
      is_mod = 1;
    }
    if (is_mod)
      (*fmt)++;
  } while (is_mod);
}

static int print_arg(char *out, const char **fmt, va_list *ap) {
  char *start = out;
  uint32_t flags = 0;
  int width = -1;
  int precision = -1;
  int long_mod = 0;

  parse_arg(fmt, ap, &flags, &width, &precision, &long_mod);

  char type = *(*fmt)++;
  char *str_arg;
  unsigned long long num_val = 0;
  int is_neg = 0;

  switch (type) {
    case 's':
      str_arg = va_arg(*ap, char *);
      if (!str_arg)
          str_arg = "(null)";
      int len = strlen(str_arg);
      if (precision >= 0 && len > precision)
          len = precision;
      
      int fill_len = width > len ? width - len : 0;
      if (!(flags & F_LEFT)) {
          while (fill_len--) *out++ = ' ';
      }
      memcpy(out, str_arg, len);
      out += len;
      if (flags & F_LEFT) {
          while (fill_len--) *out++ = ' ';
      }
      break;

    case 'd': {
      long long val;
      if (long_mod == 0) 
        val = va_arg(*ap, int);
      else if (long_mod == 1) 
        val = va_arg(*ap, long);
      else 
        val = va_arg(*ap, long long);
      
      if (val < 0) {
        is_neg = 1;
        num_val = (unsigned long long)(-val);
      } else {
        num_val = (unsigned long long)val;
      }
      out += print_num(out, num_val, 10, width, flags, is_neg);
      break;
    }
    
    case 'x': {
      if (long_mod == 0)
        num_val = (unsigned int)va_arg(*ap, int);
      else if (long_mod == 1)
        num_val = (unsigned long)va_arg(*ap, long);
      else
        num_val = (unsigned long long)va_arg(*ap, long long);
      
      out += print_num(out, num_val, 16, width, flags, 0);
      break;
    }

    case 'p':
      num_val = (uintptr_t)va_arg(*ap, void *);
      flags |= F_ALT; 
      out += print_num(out, num_val, 16, width, flags, 0);
      break;

    case 'c':
      if (!(flags & F_LEFT)) {
        while (width-- > 1)
          *out++ = ' ';
      }
      *out++ = (char)va_arg(*ap, int);
      break;

    case '%':
      *out++ = '%';
      break;

    default:
      *out++ = '%';
      *out++ = type;
      break;
  }

  return out - start;
}

int printf(const char *fmt, ...) {
  panic("Not implemented");
}

int vsprintf(char *out, const char *fmt, va_list ap) {
  int cnt = 0;
  while (*fmt) {
    if (*fmt == '%') {
      int len = print_arg(out, &fmt, &ap);    
      cnt += len;
      out += len;
    } else {
      *out++ = *fmt++;
      cnt++;
    }
  }
  *out = '\0';
  return cnt;
}

int sprintf(char *out, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  int ret = vsprintf(out, fmt, ap);
  va_end(ap);
  return ret;
}

int snprintf(char *out, size_t n, const char *fmt, ...) {
  panic("Not implemented");
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap) {
  panic("Not implemented");
}

#endif

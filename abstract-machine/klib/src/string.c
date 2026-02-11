#include <klib.h>
#include <klib-macros.h>
#include <stdint.h>
#include <string.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

size_t strlen(const char *s) {
  size_t i;
  for (i = 0; s[i] != '\0'; i++);
  return i;
}

char *stpcpy(char *dst, const char *src) {
  char *p = mempcpy(dst, src, strlen(src));
  *p = '\0';
  return p;
}

char *strcpy(char *dst, const char *src) {
  stpcpy(dst, src);
  return dst;
}

char *strncpy(char *dst, const char *src, size_t n) {
  panic("Not implemented");
}

char *strcat(char *dst, const char *src) {
  stpcpy(dst + strlen(dst), src);
  return dst;
}

int strcmp(const char *s1, const char *s2) {
  while (1) {
    int result = (int)*s1 - (int)*s2;
    if (result != 0 || *s1 == '\0' || *s2 == '\0')
      return result;
    s1++;
    s2++;
  }
}

int strncmp(const char *s1, const char *s2, size_t n) {
  // int i;
  // for (i = 0; i < n; i++) {
  //   int result = (int)*s1 - (int)*s2;
  //   if (result != 0 || *s1 == '\0' || *s2 == '\0')
  //     return result;
  //   s1++;
  //   s2++;
  // }
  // return 0;
  if (!n) return 0;
  while (--n && *s1 && (*s1 == *s2)) {
    s1++;
    s2++;
  }
  return *s1 - *s2;
}

void *memset(void *s, int c, size_t n) {
  int i;
  for (i = 0; i < n; i++) {
    ((unsigned char *)s)[i] = (unsigned char)c; 
  }  
  return s;
}

void *memmove(void *dst, const void *src, size_t n) {
  void *tmp = malloc(n);
  memcpy(tmp, src, n);
  memcpy(dst, tmp, n);
  free(tmp);
  return dst;
}

void *mempcpy(void *out, const void *in, size_t n) {
  int i;
  for (i = 0; i < n; i++) {
    ((unsigned char *)out)[i] = ((unsigned char *)in)[i];
  }
  return (unsigned char *)out + n;
}

void *memcpy(void *out, const void *in, size_t n) {
  mempcpy(out, in, n);
  return out;
}

int memcmp(const void *s1, const void *s2, size_t n) {
  int i;
  for (i = 0; i < n; i++) {
    int result = ((unsigned char *)s1)[i] - ((unsigned char *)s2)[i];
    if (result != 0)
      return result;
  }
  return 0;
}

#endif

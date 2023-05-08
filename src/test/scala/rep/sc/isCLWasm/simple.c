#include <stdlib.h>
#include <stdio.h>
#define Export __attribute__((visibility("default")))

Export
void *_allocate(size_t size) {
  return malloc(size);
}

Export
void _deallocate(void *p) {
  free(p);
}

typedef struct {
  int _len;
  signed char *_data;
} _string;

typedef struct {
  int a;
  _string b;
} S1;

typedef struct {
  int _capacity;
  int _len;
  int *_data;
} _list_int;

//extern void *malloc(unsigned long long);

Export
_Bool _success;

Export
_string _msg;

Export
S1 s1;

signed char __p_tmp1[3] = { 97, 98, 99, };

_string _p_tmp1 = { 3, __p_tmp1, };

signed char __out_of_bounds[19] = { 105, 110, 100, 101, 120, 32, 111, 117,
  116, 32, 111, 102, 32, 98, 111, 117, 110, 100, 115, };

_string _out_of_bounds = { 19, __out_of_bounds, };

signed char __div_by_zero[33] = { 97, 114, 105, 116, 104, 109, 101, 116, 105,
  99, 32, 101, 120, 99, 101, 112, 116, 105, 111, 110, 58, 32, 100, 105, 118,
  32, 98, 121, 32, 122, 101, 114, 111, };

_string _div_by_zero = { 33, __div_by_zero, };

signed char __mod_by_zero[33] = { 97, 114, 105, 116, 104, 109, 101, 116, 105,
  99, 32, 101, 120, 99, 101, 112, 116, 105, 111, 110, 58, 32, 109, 111, 100,
  32, 98, 121, 32, 122, 101, 114, 111, };

_string _mod_by_zero = { 33, __mod_by_zero, };

signed char __get_none[25] = { 99, 97, 110, 39, 116, 32, 103, 101, 116, 32,
  118, 97, 108, 117, 101, 32, 102, 114, 111, 109, 32, 110, 111, 110, 101, };

_string _get_none = { 25, __get_none, };

signed char __malloc_failed[13] = { 109, 97, 108, 108, 111, 99, 32, 102, 97,
  105, 108, 101, 100, };

_string _malloc_failed = { 13, __malloc_failed, };

void _init_string(_string *_out)
{
  (*_out)._len = 0;
}

void _assign_string(_string *_a1, _string *_out)
{
  int _i;
  register signed char *$67;
  (*_out)._len = (*_a1)._len;
  $67 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $67;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[_i];
  }
}

void _equals_string(_string *_a1, _string *_a2, _Bool *_out)
{
  int _i;
  *_out = 0;
  if ((*_a1)._len != (*_a2)._len) {
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a1)._len)) {
      break;
    }
    if ((*_a1)._data[_i] != (*_a2)._data[_i]) {
      return;
    }
  }
  *_out = 1;
}

void _concat_string(_string *_a1, _string *_a2, _string *_out)
{
  int _i;
  register signed char *$67;
  (*_out)._len = (*_a1)._len + (*_a2)._len;
  $67 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $67;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a1)._len)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[_i];
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a2)._len)) {
      break;
    }
    (*_out)._data[(_i + (*_a1)._len)] = (*_a2)._data[_i];
  }
}

void _substring_string(_string *_a1, int _a2, int _a3, _string *_out)
{
  int _i;
  register signed char *$67;
  (*_out)._len = _a3 - _a2;
  $67 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $67;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[(_i + _a2)];
  }
}

void _init_S1(S1 *_out)
{
  (*_out).a = 0;
  _init_string(&(*_out).b);
  if (_success == 0) {
    return;
  }
}

void _assign_S1(S1 *_a1, S1 *_out)
{
  (*_out).a = (*_a1).a;
  _assign_string(&(*_a1).b, &(*_out).b);
  if (_success == 0) {
    return;
  }
}

void _init_list_int(_list_int *_out)
{
  int _i;
  register int *$67;
  (*_out)._capacity = 4;
  (*_out)._len = 0;
  $67 = malloc(sizeof(int) * (*_out)._capacity);
  (*_out)._data = $67;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._capacity)) {
      break;
    }
    (*_out)._data[_i] = 0;
  }
}

void _assign_list_int(_list_int *_a1, _list_int *_out)
{
  int _i;
  register int *$67;
  (*_out)._capacity = (*_a1)._capacity;
  (*_out)._len = (*_a1)._len;
  $67 = malloc(sizeof(int) * (*_out)._capacity);
  (*_out)._data = $67;
  if ((*_out)._data == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._capacity)) {
      break;
    }
    (*_out)._data[_i] = (*_a1)._data[_i];
  }
}

void _add_list_int(int _a1, _list_int *_out)
{
  int _i;
  int *_t1;
  register int *$67;
  if ((*_out)._len == (*_out)._capacity) {
    (*_out)._capacity = (*_out)._capacity * 2;
    $67 = malloc(sizeof(int) * (*_out)._capacity);
    _t1 = $67;
    if (_t1 == 0) {
      _success = 0;
      _msg = _malloc_failed;
      return;
    }
    _i = 0;
    for (; 1; _i = _i + 1) {
      if (! (_i < (*_out)._len)) {
        break;
      }
      _t1[_i] = (*_out)._data[_i];
    }
    (*_out)._data = _t1;
  }
  (*_out)._data[(*_out)._len] = _a1;
  (*_out)._len = (*_out)._len + 1;
}

void _get_list_int(_list_int *_a1, int _a2, int *_out)
{
  *_out = (*_a1)._data[_a2];
}

void _set_list_int(int _a1, int _a2, _list_int *_out)
{
  (*_out)._data[_a1] = _a2;
}

Export
void get_from_list(_list_int *_list, int _index, int *_out)
{
  _list_int list;
  int index;
  _init_list_int(&list);
  if (_success == 0) {
    return;
  }
  index = 0;
  _assign_list_int(_list, &list);
  if (_success == 0) {
    return;
  }
  index = _index;
  if (-1 >= index || index >= list._len) {
    _success = 0;
    _msg = _out_of_bounds;
    return;
  }
  _get_list_int(&list, index, _out);
  if (_success == 0) {
    return;
  }
  return;
}

Export
void g(int _index)
{
  void* p = malloc(0);
  _list_int list;
  int _f_tmp1;
  int index;
  _init_list_int(&list);
  if (_success == 0) {
    return;
  }
  _f_tmp1 = 0;
  index = 0;
  index = _index;
  /*skip*/;
  _add_list_int(1, &list);
  if (_success == 0) {
    return;
  }
  _add_list_int(2, &list);
  if (_success == 0) {
    return;
  }
  _add_list_int(3, &list);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  get_from_list(&list, index, &_f_tmp1);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  s1.a = _f_tmp1;
  _assign_string(&_p_tmp1, &s1.b);
  if (_success == 0) {
    return;
  }
}

Export
void _init(_Bool *__success, _string *__msg, S1 *_s1)
{
  _success = *__success;
  _assign_string(__msg, &_msg);
  if (_success == 0) {
    return;
  }
  _assign_S1(_s1, &s1);
  if (_success == 0) {
    return;
  }
}

Export
void _terminate(_Bool *__success, _string *__msg, S1 *_s1)
{
  *__success = _success;
  _assign_string(&_msg, __msg);
  if (_success == 0) {
    return;
  }
  _assign_S1(&s1, _s1);
  if (_success == 0) {
    return;
  }
}



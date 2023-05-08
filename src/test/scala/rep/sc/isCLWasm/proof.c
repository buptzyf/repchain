#include <stdlib.h>
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
  _Bool _some;
  _string _data;
} _option_string;

typedef struct {
  int _capacity;
  int _len;
  _string *_key;
  _string *_value;
} _map_string_string;

//extern void *malloc(unsigned long long);

Export
_Bool _success;

Export
_string _msg;

Export
_map_string_string proof;

signed char __p_tmp1[1] = { 97, };

_string _p_tmp1 = { 1, __p_tmp1, };

signed char __p_tmp2[1] = { 98, };

_string _p_tmp2 = { 1, __p_tmp2, };

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
  register signed char *$65;
  (*_out)._len = (*_a1)._len;
  $65 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $65;
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
  register signed char *$65;
  (*_out)._len = (*_a1)._len + (*_a2)._len;
  $65 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $65;
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
  register signed char *$65;
  (*_out)._len = _a3 - _a2;
  $65 = malloc(sizeof(signed char) * (*_out)._len);
  (*_out)._data = $65;
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

void _init_option_string(_option_string *_out)
{
  (*_out)._some = 0;
  _init_string(&(*_out)._data);
  if (_success == 0) {
    return;
  }
}

void _assign_option_string(_option_string *_a1, _option_string *_out)
{
  (*_out)._some = (*_a1)._some;
  _assign_string(&(*_a1)._data, &(*_out)._data);
  if (_success == 0) {
    return;
  }
}

void _init_map_string_string(_map_string_string *_out)
{
  int _i;
  register _string *$65;
  register _string *$66;
  (*_out)._capacity = 4;
  (*_out)._len = 0;
  $65 = malloc(sizeof(_string) * (*_out)._capacity);
  (*_out)._key = $65;
  if ((*_out)._key == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  $66 = malloc(sizeof(_string) * (*_out)._capacity);
  (*_out)._value = $66;
  if ((*_out)._value == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._capacity)) {
      break;
    }
    _init_string((*_out)._key + _i);
    if (_success == 0) {
      return;
    }
    _init_string((*_out)._value + _i);
    if (_success == 0) {
      return;
    }
  }
}

void _assign_map_string_string(_map_string_string *_a1, _map_string_string *_out)
{
  int _i;
  register _string *$65;
  register _string *$66;
  (*_out)._capacity = (*_a1)._capacity;
  (*_out)._len = (*_a1)._len;
  $65 = malloc(sizeof(_string) * (*_out)._capacity);
  (*_out)._key = $65;
  if ((*_out)._key == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  $66 = malloc(sizeof(_string) * (*_out)._capacity);
  (*_out)._value = $66;
  if ((*_out)._value == 0) {
    _success = 0;
    _msg = _malloc_failed;
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._capacity)) {
      break;
    }
    _assign_string((*_a1)._key + _i, (*_out)._key + _i);
    if (_success == 0) {
      return;
    }
    _assign_string((*_a1)._value + _i, (*_out)._value + _i);
    if (_success == 0) {
      return;
    }
  }
}

void _get_map_string_string(_map_string_string *_a1, _string *_a2, _option_string *_out)
{
  int _i;
  _Bool _v1;
  _init_option_string(_out);
  if (_success == 0) {
    return;
  }
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_a1)._len)) {
      break;
    }
    _equals_string((*_a1)._key + _i, _a2, &_v1);
    if (_success == 0) {
      return;
    }
    if (_v1 == 1) {
      (*_out)._some = 1;
      _assign_string((*_a1)._value + _i, &(*_out)._data);
      if (_success == 0) {
        return;
      }
      return;
    }
  }
}

void _set_map_string_string(_string *_a1, _string *_a2, _map_string_string *_out)
{
  int _i;
  _Bool _v1;
  _string *_t1;
  _string *_t2;
  register _string *$65;
  register _string *$66;
  _i = 0;
  for (; 1; _i = _i + 1) {
    if (! (_i < (*_out)._len)) {
      break;
    }
    _equals_string((*_out)._key + _i, _a1, &_v1);
    if (_success == 0) {
      return;
    }
    if (_v1 == 1) {
      _assign_string(_a2, (*_out)._value + _i);
      if (_success == 0) {
        return;
      }
      return;
    }
  }
  if ((*_out)._len == (*_out)._capacity) {
    (*_out)._capacity = (*_out)._capacity * 2;
    $65 = malloc(sizeof(_string) * (*_out)._capacity);
    _t1 = $65;
    if (_t1 == 0) {
      _success = 0;
      _msg = _malloc_failed;
      return;
    }
    $66 = malloc(sizeof(_string) * (*_out)._capacity);
    _t2 = $66;
    if (_t2 == 0) {
      _success = 0;
      _msg = _malloc_failed;
      return;
    }
    _i = 0;
    for (; 1; _i = _i + 1) {
      if (! (_i < (*_out)._capacity)) {
        break;
      }
      _init_string(_t1 + _i);
      if (_success == 0) {
        return;
      }
      _init_string(_t2 + _i);
      if (_success == 0) {
        return;
      }
    }
    _i = 0;
    for (; 1; _i = _i + 1) {
      if (! (_i < (*_out)._len)) {
        break;
      }
      _assign_string((*_out)._key + _i, _t1 + _i);
      if (_success == 0) {
        return;
      }
      _assign_string((*_out)._value + _i, _t2 + _i);
      if (_success == 0) {
        return;
      }
    }
    (*_out)._key = _t1;
    (*_out)._value = _t2;
  }
  _assign_string(_a1, (*_out)._key + (*_out)._len);
  if (_success == 0) {
    return;
  }
  _assign_string(_a2, (*_out)._value + (*_out)._len);
  if (_success == 0) {
    return;
  }
  (*_out)._len = (*_out)._len + 1;
}

Export
void putProof(_string *_key, _string *_value)
{
  _map_string_string _f_tmp1;
  int _f_tmp2;
  _map_string_string _f_tmp3;
  int _f_tmp4;
  _string key;
  _string value;
  _init_map_string_string(&_f_tmp1);
  if (_success == 0) {
    return;
  }
  _f_tmp2 = 0;
  _init_map_string_string(&_f_tmp3);
  if (_success == 0) {
    return;
  }
  _f_tmp4 = 0;
  _init_string(&key);
  if (_success == 0) {
    return;
  }
  _init_string(&value);
  if (_success == 0) {
    return;
  }
  _assign_string(_key, &key);
  if (_success == 0) {
    return;
  }
  _assign_string(_value, &value);
  if (_success == 0) {
    return;
  }
  /*skip*/;
  _set_map_string_string(&_p_tmp1, &_p_tmp2, &_f_tmp1);
  if (_success == 0) {
    return;
  }
  _f_tmp2 = 0;
  for (; 1; _f_tmp2 = _f_tmp2 + 1) {
    if (! (_f_tmp2 < _f_tmp1._len)) {
      break;
    }
    _set_map_string_string
      (_f_tmp1._key + _f_tmp2, _f_tmp1._value + _f_tmp2, &proof);
    if (_success == 0) {
      return;
    }
  }
  /*skip*/;
  _set_map_string_string(&key, &value, &_f_tmp3);
  if (_success == 0) {
    return;
  }
  _f_tmp4 = 0;
  for (; 1; _f_tmp4 = _f_tmp4 + 1) {
    if (! (_f_tmp4 < _f_tmp3._len)) {
      break;
    }
    _set_map_string_string
      (_f_tmp3._key + _f_tmp4, _f_tmp3._value + _f_tmp4, &proof);
    if (_success == 0) {
      return;
    }
  }
}

Export
void _init(_Bool *__success, _string *__msg, _map_string_string *_proof)
{
  _success = *__success;
  _assign_string(__msg, &_msg);
  if (_success == 0) {
    return;
  }
  _assign_map_string_string(_proof, &proof);
  if (_success == 0) {
    return;
  }
}

Export
void _terminate(_Bool *__success, _string *__msg, _map_string_string *_proof)
{
  *__success = _success;
  _assign_string(&_msg, __msg);
  if (_success == 0) {
    return;
  }
  _assign_map_string_string(&proof, _proof);
  if (_success == 0) {
    return;
  }
}



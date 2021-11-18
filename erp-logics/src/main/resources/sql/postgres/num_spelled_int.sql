-- FUNCTION: public.num_spelled_int(numeric, character, text[])

-- DROP FUNCTION public.num_spelled_int(numeric, character, text[]);

CREATE OR REPLACE FUNCTION public.num_spelled_int(
	n numeric,
	g character,
	d text[])
    RETURNS text
    LANGUAGE 'plpgsql'

    COST 100
    VOLATILE

AS $BODY$
declare
  r text;
  s text[];
begin
  r := ltrim(to_char(n, '9,9,,9,,,,,,9,9,,9,,,,,9,9,,9,,,,9,9,,9,,,.')) || '.';

  if array_upper(d,1) = 1 and d[1] is not null then
    s := array[ d[1], d[1], d[1] ];
  else
    s := array[ coalesce(d[1],''), coalesce(d[2],''), coalesce(d[3],'') ];
  end if;

  --t - тысячи; m - милионы; M - миллиарды;
  r := replace( r, ',,,,,,', 'eM');
  r := replace( r, ',,,,,', 'em');
  r := replace( r, ',,,,', 'et');
  --e - единицы; d - десятки; c - сотни;
  r := replace( r, ',,,', 'e');
  r := replace( r, ',,', 'd');
  r := replace( r, ',', 'c');
  --удаление незначащих нулей
  r := replace( r, '0c0d0et', '');
  r := replace( r, '0c0d0em', '');
  r := replace( r, '0c0d0eM', '');

  --сотни
  r := replace( r, '0c', '');
  r := replace( r, '1c', 'сто ');
  r := replace( r, '2c', 'двести ');
  r := replace( r, '3c', 'триста ');
  r := replace( r, '4c', 'четыреста ');
  r := replace( r, '5c', 'пятьсот ');
  r := replace( r, '6c', 'шестьсот ');
  r := replace( r, '7c', 'семьсот ');
  r := replace( r, '8c', 'восемьсот ');
  r := replace( r, '9c', 'девятьсот ');

  --десятки
  r := replace( r, '1d0e', 'десять ');
  r := replace( r, '1d1e', 'одиннадцать ');
  r := replace( r, '1d2e', 'двенадцать ');
  r := replace( r, '1d3e', 'тринадцать ');
  r := replace( r, '1d4e', 'четырнадцать ');
  r := replace( r, '1d5e', 'пятнадцать ');
  r := replace( r, '1d6e', 'шестнадцать ');
  r := replace( r, '1d7e', 'семнадцать ');
  r := replace( r, '1d8e', 'восемнадцать ');
  r := replace( r, '1d9e', 'девятнадцать ');
  r := replace( r, '0d', '');
  r := replace( r, '2d', 'двадцать ');
  r := replace( r, '3d', 'тридцать ');
  r := replace( r, '4d', 'сорок ');
  r := replace( r, '5d', 'пятьдесят ');
  r := replace( r, '6d', 'шестьдесят ');
  r := replace( r, '7d', 'семьдесят ');
  r := replace( r, '8d', 'восемьдесят ');
  r := replace( r, '9d', 'девяносто ');

  --единицы
  r := replace( r, '0e', '');
  r := replace( r, '5e', 'пять ');
  r := replace( r, '6e', 'шесть ');
  r := replace( r, '7e', 'семь ');
  r := replace( r, '8e', 'восемь ');
  r := replace( r, '9e', 'девять ');

  if g = 'M' then
    r := replace( r, '1e.', 'один !'||s[1]||' '); --один рубль
    r := replace( r, '2e.', 'два !'||s[2]||' '); --два рубля
  elsif g = 'F' then
    r := replace( r, '1e.', 'одна !'||s[1]||' '); --одна тонна
    r := replace( r, '2e.', 'две !'||s[2]||' '); --две тонны
  elsif g = 'N' then
    r := replace( r, '1e.', 'одно !'||s[1]||' '); --одно место
    r := replace( r, '2e.', 'два !'||s[2]||' '); --два места
  end if;
  r := replace( r, '3e.', 'три !'||s[2]||' ');
  r := replace( r, '4e.', 'четыре !'||s[2]||' ');

  r := replace( r, '1et', 'одна тысяча ');
  r := replace( r, '2et', 'две тысячи ');
  r := replace( r, '3et', 'три тысячи ');
  r := replace( r, '4et', 'четыре тысячи ');
  r := replace( r, '1em', 'один миллион ');
  r := replace( r, '2em', 'два миллиона ');
  r := replace( r, '3em', 'три миллиона ');
  r := replace( r, '4em', 'четыре миллиона ');
  r := replace( r, '1eM', 'один милиард ');
  r := replace( r, '2eM', 'два милиарда ');
  r := replace( r, '3eM', 'три милиарда ');
  r := replace( r, '4eM', 'четыре милиарда ');

  r := replace( r, 't', 'тысяч ');
  r := replace( r, 'm', 'миллионов ');
  r := replace( r, 'M', 'милиардов ');

  r := replace( r, '.', ' !'||s[3]||' ');

  if n = 0 then
    r := 'ноль ' || r;
  end if;

  return r;
end;
$BODY$;

ALTER FUNCTION public.num_spelled_int(numeric, character, text[])
    OWNER TO postgres;

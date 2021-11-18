-- FUNCTION: public.num_spelled(numeric, character, text[], character, text[], text)

-- DROP FUNCTION public.num_spelled(numeric, character, text[], character, text[], text);

CREATE OR REPLACE FUNCTION public.num_spelled(
	source_number numeric,
	int_unit_gender character,
	int_units text[],
	frac_unit_gender character,
	frac_units text[],
	frac_format text)
    RETURNS text
    LANGUAGE 'plpgsql'

    COST 100
    VOLATILE

AS $BODY$
declare
  i numeric;
  f numeric;
  fmt text;
  fs  text;
  s int := 0;
  result text;
begin
  i := trunc(abs(source_number));
  fmt := regexp_replace(frac_format, '[^09]', '', 'g');
  s := char_length(fmt);
  f := round((abs(source_number) - i) * pow(10, s));

  result := num_spelled_int(i, int_unit_gender, int_units);
  fs := num_spelled_int(f, frac_unit_gender, frac_units);

  if coalesce(s,0) > 0 then --дробная часть
    if frac_format like '%d%' then --цифрами
      fs := to_char(f, fmt) || ' ' || substring(fs, '!.*');
    end if;
    if frac_format like '%m%' then --между целой частью и ед.изм.
      result := replace(result, '!', ', '||fs||' ');
    else --в конце
      result := result || ' ' || fs;
    end if;
  end if;
  result := replace(result, '!', '');
  result := regexp_replace(result, ' +', ' ', 'g'); --лишние пробелы
  result := replace(result, ' ,', ',');

  if source_number < 0 then
    result := 'минус ' || result;
  end if;

  return trim(result);
end;
$BODY$;

ALTER FUNCTION public.num_spelled(numeric, character, text[], character, text[], text)
    OWNER TO postgres;

COMMENT ON FUNCTION public.num_spelled(numeric, character, text[], character, text[], text)
    IS 'Число прописью.
source_number    numeric   исходное число
int_unit_gender  char      род целой единицы измерения (F/M/N)
int_units        text[]    названия целых единиц (3 элемента):
                           [1] - 1 рубль/1 тонна/1 место
                           [2] - 2 рубля/2 тонны/2 места
                           [3] - 0 рублей/0 тонн/0 мест
frac_unit_gender char      род дробной единицы измерения (F/M/N)
frac_units       text      названия дробных единиц (3 элемента):
                           [1] - 1 грамм/1 копейка
                           [2] - 2 грамма/2 копейки
                           [3] - 0 граммов/0 копеек
frac_format      text      каким образом выводить дроби:
                           ''0'' - число разрядов, с ведущими нулями
                           ''9'' - число разрядов, без ведущих нулей
                           ''t'' - текстом (''00t'' -> четыре рубля двадцать копеек)
                           ''d'' - цифрами (''00d'' -> четыре рубля 20 копеек)
                           ''m'' - выводить дробную часть перед единицей измерения целой части
                             (''00dm'' -> четыре, 20 рубля)
';

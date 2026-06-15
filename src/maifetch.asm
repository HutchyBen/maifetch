default rel

global main

extern atoi
extern exit
extern fclose
extern fopen
extern fprintf
extern fread
extern free
extern fseek
extern ftell
extern getenv
extern malloc
extern printf
extern putchar
extern puts
extern snprintf
extern strcmp
extern strlen
extern strncmp
extern strstr
extern system
extern stderr

section .data
default_config     db ".config/maifetch.json", 0
config_suffix      db "%s/maifetch.json", 0
home_config_suffix db "%s/.config/maifetch.json", 0
read_mode          db "rb", 0

env_home           db "HOME", 0
env_xdg            db "XDG_CONFIG_HOME", 0
env_config         db "MAITEA_CONFIG_FILE", 0
env_token          db "MAITEA_TOKEN", 0
env_logo           db "MAITEA_LOGO_SIZE", 0
env_scores         db "MAITEA_SCORE_COUNT", 0

opt_help_short     db "-h", 0
opt_help           db "--help", 0
opt_no_color       db "--no-color", 0
opt_a              db "-a", 0
opt_t              db "-t", 0
opt_access         db "--access-token", 0
opt_l              db "-l", 0
opt_logo           db "--logo-size", 0
opt_s              db "-s", 0
opt_score          db "--score-count", 0
opt_c              db "-c", 0
opt_config         db "--config-file", 0
opt_profiles       db "--profiles-fixture", 0
opt_plays          db "--plays-fixture", 0

opt_access_eq      db "--access-token=", 0
opt_access_eq_len  equ $ - opt_access_eq - 1
opt_logo_eq        db "--logo-size=", 0
opt_logo_eq_len    equ $ - opt_logo_eq - 1
opt_score_eq       db "--score-count=", 0
opt_score_eq_len   equ $ - opt_score_eq - 1
opt_config_eq      db "--config-file=", 0
opt_config_eq_len  equ $ - opt_config_eq - 1
opt_profiles_eq    db "--profiles-fixture=", 0
opt_profiles_eq_len equ $ - opt_profiles_eq - 1
opt_plays_eq       db "--plays-fixture=", 0
opt_plays_eq_len   equ $ - opt_plays_eq - 1

help_1             db "Usage: maifetch [options]", 0
help_2             db "", 0
help_3             db "Options:", 0
help_4             db "  -a, -t, --access-token TOKEN    Access token for the MaiTea account", 0
help_5             db "  -l, --logo-size SIZE            Size of the ASCII logo (<1 disables)", 0
help_6             db "  -s, --score-count COUNT         Amount of recent scores to show (max 12)", 0
help_7             db "  -c, --config-file FILE          Config file to use", 0
help_8             db "      --profiles-fixture FILE     Read profiles JSON from a fixture file", 0
help_9             db "      --plays-fixture FILE        Read plays JSON from a fixture file", 0
help_10            db "      --no-color                  Accepted for compatibility", 0
help_11            db "  -h, --help                      Show this help", 0

pat_access_token   db '"accessToken"', 0
pat_logo_size      db '"logoSize"', 0
pat_score_count    db '"scoreCount"', 0
pat_data           db '"data"', 0
pat_name           db '"name"', 0
pat_id             db '"id"', 0
pat_rating         db '"rating"', 0
pat_rating_highest db '"rating_highest"', 0
pat_level          db '"level"', 0
pat_total          db '"total"', 0
pat_en             db '"en"', 0
pat_value          db '"value"', 0
pat_score_fmt      db '"score_formatted"', 0
pat_ach_fmt        db '"achievement_formatted"', 0
pat_rank           db '"rank"', 0
pat_combo          db '"full_combo_label"', 0

api_profiles       db "/api/v1/profiles", 0
api_plays          db "/api/v1/plays", 0
tmp_profiles       db "/tmp/maifetch_profiles.json", 0
tmp_plays          db "/tmp/maifetch_plays.json", 0
curl_fmt           db "curl -fsSL -H 'Authorization: Bearer %s' -H 'Accept: application/json' -H 'Content-Type: application/json' 'https://maitea.app%s' -o '%s'", 0

fmt_err            db "%s", 10, 0
fmt_unknown        db "unknown argument: %s", 10, 0
fmt_missing        db "missing value for %s", 10, 0
fmt_str            db "%s", 10, 0
fmt_id             db "ID: %ld", 10, 0
fmt_rating         db "Rating: %ld.%02ld / %ld.%02ld", 10, 0
fmt_level          db "Level: %ld", 10, 0
fmt_total          db "Total Credits: %ld", 10, 0
fmt_score_title    db "  %s  %s", 10, 0
fmt_score_line     db "  %s %s%% %s %s", 10, 10, 0
fmt_fetch_fail     db "MaiTea API request failed", 0

msg_access         db "access token is required", 0
msg_score          db "score count cannot be higher than 12", 0
msg_read           db "could not read file", 0
msg_no_data        db "response did not include a data array", 0
msg_no_profiles    db "No profiles found", 0
default_name       db "MaiTea", 0
empty              db "", 0
recent_scores      db "Recent Scores:", 0

section .bss
argc_store         resq 1
argv_store         resq 1
config_file        resq 1
access_token       resq 1
profiles_fixture   resq 1
plays_fixture      resq 1
logo_size          resq 1
score_count        resq 1
no_color           resq 1
cli_token          resq 1
cli_config         resq 1
cli_logo           resq 1
cli_score          resq 1
has_cli_logo       resq 1
has_cli_score      resq 1
config_path_buf    resb 512
command_buf        resb 4096
restore_ptr        resq 1
restore_char       resb 1
loop_count         resq 1
score_limit        resq 1
tmp_profile_json   resq 1
tmp_plays_json     resq 1
rank_value         resq 1

section .text

main:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  sub rsp, 8

  mov [argc_store], rdi
  mov [argv_store], rsi
  call init_default_config
  call parse_args
  call apply_config

  mov rax, [profiles_fixture]
  test rax, rax
  jz .check_token
  mov rax, [plays_fixture]
  test rax, rax
  jnz .load_inputs

.check_token:
  mov rax, [access_token]
  test rax, rax
  jnz .load_inputs
  lea rdi, [msg_access]
  call die

.load_inputs:
  mov rdi, [profiles_fixture]
  lea rsi, [api_profiles]
  lea rdx, [tmp_profiles]
  call load_json
  mov [tmp_profile_json], rax

  mov rdi, [plays_fixture]
  lea rsi, [api_plays]
  lea rdx, [tmp_plays]
  call load_json
  mov [tmp_plays_json], rax

  mov rdi, [tmp_profile_json]
  mov rsi, [tmp_plays_json]
  call print_from_json

  mov rdi, [tmp_profile_json]
  call free
  mov rdi, [tmp_plays_json]
  call free

  xor eax, eax
  add rsp, 8
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

die:
  push rbp
  mov rbp, rsp
  mov rdx, rdi
  mov rdi, [stderr]
  lea rsi, [fmt_err]
  xor eax, eax
  call fprintf
  mov edi, 1
  call exit

print_help:
  push rbp
  mov rbp, rsp
  lea rdi, [help_1]
  call puts
  lea rdi, [help_2]
  call puts
  lea rdi, [help_3]
  call puts
  lea rdi, [help_4]
  call puts
  lea rdi, [help_5]
  call puts
  lea rdi, [help_6]
  call puts
  lea rdi, [help_7]
  call puts
  lea rdi, [help_8]
  call puts
  lea rdi, [help_9]
  call puts
  lea rdi, [help_10]
  call puts
  lea rdi, [help_11]
  call puts
  xor edi, edi
  call exit

init_default_config:
  push rbp
  mov rbp, rsp
  mov qword [logo_size], 20
  mov qword [score_count], 4
  mov qword [config_file], default_config

  lea rdi, [env_xdg]
  call getenv
  test rax, rax
  jz .try_home
  lea rdi, [config_path_buf]
  mov esi, 512
  lea rdx, [config_suffix]
  mov rcx, rax
  xor eax, eax
  call snprintf
  lea rax, [config_path_buf]
  mov [config_file], rax
  pop rbp
  ret

.try_home:
  lea rdi, [env_home]
  call getenv
  test rax, rax
  jz .done
  lea rdi, [config_path_buf]
  mov esi, 512
  lea rdx, [home_config_suffix]
  mov rcx, rax
  xor eax, eax
  call snprintf
  lea rax, [config_path_buf]
  mov [config_file], rax

.done:
  pop rbp
  ret

parse_args:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  push r14
  push r15
  sub rsp, 8

  mov r12, [argv_store]
  mov r13, [argc_store]
  mov rbx, 1

.loop:
  cmp rbx, r13
  jge .done
  mov r14, [r12 + rbx * 8]

  mov rdi, r14
  lea rsi, [opt_help_short]
  call strcmp
  test eax, eax
  jz .help
  mov rdi, r14
  lea rsi, [opt_help]
  call strcmp
  test eax, eax
  jz .help
  mov rdi, r14
  lea rsi, [opt_no_color]
  call strcmp
  test eax, eax
  jz .set_no_color

  mov rdi, r14
  lea rsi, [opt_a]
  call strcmp
  test eax, eax
  jz .need_token
  mov rdi, r14
  lea rsi, [opt_t]
  call strcmp
  test eax, eax
  jz .need_token
  mov rdi, r14
  lea rsi, [opt_access]
  call strcmp
  test eax, eax
  jz .need_token

  mov rdi, r14
  lea rsi, [opt_l]
  call strcmp
  test eax, eax
  jz .need_logo
  mov rdi, r14
  lea rsi, [opt_logo]
  call strcmp
  test eax, eax
  jz .need_logo

  mov rdi, r14
  lea rsi, [opt_s]
  call strcmp
  test eax, eax
  jz .need_score
  mov rdi, r14
  lea rsi, [opt_score]
  call strcmp
  test eax, eax
  jz .need_score

  mov rdi, r14
  lea rsi, [opt_c]
  call strcmp
  test eax, eax
  jz .need_config
  mov rdi, r14
  lea rsi, [opt_config]
  call strcmp
  test eax, eax
  jz .need_config

  mov rdi, r14
  lea rsi, [opt_profiles]
  call strcmp
  test eax, eax
  jz .need_profiles
  mov rdi, r14
  lea rsi, [opt_plays]
  call strcmp
  test eax, eax
  jz .need_plays

  mov rdi, r14
  lea rsi, [opt_access_eq]
  mov edx, opt_access_eq_len
  call strncmp
  test eax, eax
  jz .eq_token
  mov rdi, r14
  lea rsi, [opt_logo_eq]
  mov edx, opt_logo_eq_len
  call strncmp
  test eax, eax
  jz .eq_logo
  mov rdi, r14
  lea rsi, [opt_score_eq]
  mov edx, opt_score_eq_len
  call strncmp
  test eax, eax
  jz .eq_score
  mov rdi, r14
  lea rsi, [opt_config_eq]
  mov edx, opt_config_eq_len
  call strncmp
  test eax, eax
  jz .eq_config
  mov rdi, r14
  lea rsi, [opt_profiles_eq]
  mov edx, opt_profiles_eq_len
  call strncmp
  test eax, eax
  jz .eq_profiles
  mov rdi, r14
  lea rsi, [opt_plays_eq]
  mov edx, opt_plays_eq_len
  call strncmp
  test eax, eax
  jz .eq_plays

  mov rdi, [stderr]
  lea rsi, [fmt_unknown]
  mov rdx, r14
  xor eax, eax
  call fprintf
  mov edi, 1
  call exit

.help:
  call print_help

.set_no_color:
  mov qword [no_color], 1
  inc rbx
  jmp .loop

.need_token:
  call require_next_arg
  mov [cli_token], rax
  jmp .after_next
.need_logo:
  call require_next_arg
  mov rdi, rax
  call atoi
  cdqe
  mov [cli_logo], rax
  mov qword [has_cli_logo], 1
  jmp .after_next
.need_score:
  call require_next_arg
  mov rdi, rax
  call atoi
  cdqe
  mov [cli_score], rax
  mov qword [has_cli_score], 1
  jmp .after_next
.need_config:
  call require_next_arg
  mov [cli_config], rax
  jmp .after_next
.need_profiles:
  call require_next_arg
  mov [profiles_fixture], rax
  jmp .after_next
.need_plays:
  call require_next_arg
  mov [plays_fixture], rax

.after_next:
  add rbx, 2
  jmp .loop

.eq_token:
  lea rax, [r14 + opt_access_eq_len]
  mov [cli_token], rax
  inc rbx
  jmp .loop
.eq_logo:
  lea rdi, [r14 + opt_logo_eq_len]
  call atoi
  cdqe
  mov [cli_logo], rax
  mov qword [has_cli_logo], 1
  inc rbx
  jmp .loop
.eq_score:
  lea rdi, [r14 + opt_score_eq_len]
  call atoi
  cdqe
  mov [cli_score], rax
  mov qword [has_cli_score], 1
  inc rbx
  jmp .loop
.eq_config:
  lea rax, [r14 + opt_config_eq_len]
  mov [cli_config], rax
  inc rbx
  jmp .loop
.eq_profiles:
  lea rax, [r14 + opt_profiles_eq_len]
  mov [profiles_fixture], rax
  inc rbx
  jmp .loop
.eq_plays:
  lea rax, [r14 + opt_plays_eq_len]
  mov [plays_fixture], rax
  inc rbx
  jmp .loop

.done:
  add rsp, 8
  pop r15
  pop r14
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

require_next_arg:
  mov rax, rbx
  inc rax
  cmp rax, r13
  jl .ok
  mov rdi, [stderr]
  lea rsi, [fmt_missing]
  mov rdx, r14
  xor eax, eax
  call fprintf
  mov edi, 1
  call exit
.ok:
  mov rax, [r12 + rax * 8]
  ret

apply_config:
  push rbp
  mov rbp, rsp

  lea rdi, [env_config]
  call getenv
  test rax, rax
  jz .config_cli
  mov [config_file], rax
.config_cli:
  mov rax, [cli_config]
  test rax, rax
  jz .read_config
  mov [config_file], rax
.read_config:
  mov rdi, [config_file]
  call read_json_config

  lea rdi, [env_token]
  call getenv
  test rax, rax
  jz .env_logo
  mov [access_token], rax
.env_logo:
  lea rdi, [env_logo]
  call getenv
  test rax, rax
  jz .env_score
  mov rdi, rax
  call atoi
  cdqe
  mov [logo_size], rax
.env_score:
  lea rdi, [env_scores]
  call getenv
  test rax, rax
  jz .cli_overrides
  mov rdi, rax
  call atoi
  cdqe
  mov [score_count], rax

.cli_overrides:
  mov rax, [cli_token]
  test rax, rax
  jz .cli_logo
  mov [access_token], rax
.cli_logo:
  cmp qword [has_cli_logo], 0
  je .cli_score
  mov rax, [cli_logo]
  mov [logo_size], rax
.cli_score:
  cmp qword [has_cli_score], 0
  je .validate
  mov rax, [cli_score]
  mov [score_count], rax
.validate:
  mov rax, [score_count]
  cmp rax, 12
  jle .done
  lea rdi, [msg_score]
  call die
.done:
  pop rbp
  ret

read_json_config:
  push rbp
  mov rbp, rsp
  push rbx
  push r12

  call read_file
  test rax, rax
  jz .done
  mov rbx, rax

  mov rdi, rbx
  lea rsi, [pat_access_token]
  call json_string
  test rax, rax
  jz .numbers
  mov r12, rax
  mov rdi, rax
  call strlen
  test rax, rax
  jz .numbers
  mov [access_token], r12

.numbers:
  mov rdi, rbx
  lea rsi, [pat_logo_size]
  mov rdx, [logo_size]
  call json_int
  mov [logo_size], rax
  mov rdi, rbx
  lea rsi, [pat_score_count]
  mov rdx, [score_count]
  call json_int
  mov [score_count], rax
  mov rdi, rbx
  call free
.done:
  pop r12
  pop rbx
  pop rbp
  ret

read_file:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  push r14

  mov rbx, rdi
  test rbx, rbx
  jz .fail
  mov rdi, rbx
  lea rsi, [read_mode]
  call fopen
  test rax, rax
  jz .fail
  mov r12, rax

  mov rdi, r12
  xor esi, esi
  mov edx, 2
  call fseek
  mov rdi, r12
  call ftell
  mov r13, rax
  mov rdi, r12
  xor esi, esi
  xor edx, edx
  call fseek

  lea rdi, [r13 + 1]
  call malloc
  test rax, rax
  jz .close_fail
  mov r14, rax

  mov rdi, r14
  mov esi, 1
  mov rdx, r13
  mov rcx, r12
  call fread
  mov byte [r14 + r13], 0
  mov rdi, r12
  call fclose
  mov rax, r14
  jmp .done

.close_fail:
  mov rdi, r12
  call fclose
.fail:
  xor eax, eax
.done:
  pop r14
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

json_string:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  push r14
  push r15
  sub rsp, 8

  mov rbx, rdi
  mov r12, rsi
  test rbx, rbx
  jz .not_found
  mov rdi, rbx
  mov rsi, r12
  call strstr
  test rax, rax
  jz .not_found
  mov r13, rax
  mov rdi, r12
  call strlen
  add r13, rax

.colon:
  mov al, [r13]
  test al, al
  jz .not_found
  cmp al, ':'
  je .after_colon
  inc r13
  jmp .colon
.after_colon:
  inc r13
.ws:
  mov al, [r13]
  cmp al, ' '
  je .ws_next
  cmp al, 9
  je .ws_next
  cmp al, 10
  je .ws_next
  cmp al, 13
  jne .value
.ws_next:
  inc r13
  jmp .ws
.value:
  cmp byte [r13], '"'
  jne .not_found
  inc r13
  mov r14, r13
.scan:
  mov al, [r14]
  test al, al
  jz .not_found
  cmp al, '"'
  je .copy
  inc r14
  jmp .scan
.copy:
  mov r15, r14
  sub r15, r13
  lea rdi, [r15 + 1]
  call malloc
  test rax, rax
  jz .not_found
  mov rbx, rax
  xor rcx, rcx
.copy_loop:
  cmp rcx, r15
  jge .copy_done
  mov al, [r13 + rcx]
  mov [rbx + rcx], al
  inc rcx
  jmp .copy_loop
.copy_done:
  mov byte [rbx + r15], 0
  mov rax, rbx
  jmp .done

.not_found:
  xor eax, eax
.done:
  add rsp, 8
  pop r15
  pop r14
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

json_int:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  sub rsp, 8

  mov rbx, rdx
  mov r12, rsi
  test rdi, rdi
  jz .fallback
  call strstr
  test rax, rax
  jz .fallback
  mov rdi, r12
  mov r13, rax
  call strlen
  mov rdi, r13
  add rdi, rax
.colon:
  mov al, [rdi]
  test al, al
  jz .fallback
  cmp al, ':'
  je .after_colon
  inc rdi
  jmp .colon
.after_colon:
  inc rdi
.ws:
  mov al, [rdi]
  cmp al, ' '
  je .ws_next
  cmp al, 9
  je .ws_next
  cmp al, 10
  je .ws_next
  cmp al, 13
  jne .parse
.ws_next:
  inc rdi
  jmp .ws
.parse:
  call atoi
  cdqe
  jmp .done
.fallback:
  mov rax, rbx
.done:
  add rsp, 8
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

load_json:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  push r14

  mov rbx, rdi
  mov r12, rsi
  mov r13, rdx
  test rbx, rbx
  jz .fetch
  mov rdi, rbx
  call strlen
  test rax, rax
  jz .fetch
  mov rdi, rbx
  call read_file
  test rax, rax
  jnz .done
  lea rdi, [msg_read]
  call die

.fetch:
  mov r14, [access_token]
  test r14, r14
  jnz .build
  lea rdi, [msg_access]
  call die
.build:
  lea rdi, [command_buf]
  mov esi, 4096
  lea rdx, [curl_fmt]
  mov rcx, r14
  mov r8, r12
  mov r9, r13
  xor eax, eax
  call snprintf
  lea rdi, [command_buf]
  call system
  test eax, eax
  jz .read_temp
  lea rdi, [fmt_fetch_fail]
  call die
.read_temp:
  mov rdi, r13
  call read_file
  test rax, rax
  jnz .done
  lea rdi, [msg_read]
  call die
.done:
  pop r14
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

next_object:
  xor rax, rax
  xor rdx, rdx
.seek:
  mov cl, [rdi]
  test cl, cl
  jz .none
  cmp cl, ']'
  je .none
  cmp cl, '{'
  je .start
  inc rdi
  jmp .seek
.start:
  mov rax, rdi
  xor r8d, r8d
  xor r9d, r9d
  xor r10d, r10d
.scan:
  mov cl, [rdi]
  test cl, cl
  jz .none
  cmp r9d, 0
  jne .in_string
  cmp cl, '"'
  je .enter_string
  cmp cl, '{'
  je .inc_depth
  cmp cl, '}'
  je .dec_depth
  inc rdi
  jmp .scan
.enter_string:
  mov r9d, 1
  inc rdi
  jmp .scan
.in_string:
  cmp r10d, 0
  jne .clear_escape
  cmp cl, 92
  je .set_escape
  cmp cl, '"'
  je .leave_string
  inc rdi
  jmp .scan
.set_escape:
  mov r10d, 1
  inc rdi
  jmp .scan
.clear_escape:
  xor r10d, r10d
  inc rdi
  jmp .scan
.leave_string:
  xor r9d, r9d
  inc rdi
  jmp .scan
.inc_depth:
  inc r8d
  inc rdi
  jmp .scan
.dec_depth:
  dec r8d
  cmp r8d, 0
  je .found
  inc rdi
  jmp .scan
.found:
  mov rdx, rdi
  ret
.none:
  xor rax, rax
  xor rdx, rdx
  ret

print_dashes:
  push rbp
  mov rbp, rsp
  push rbx
  sub rsp, 8
  mov rbx, rdi
.loop:
  test rbx, rbx
  jle .newline
  mov edi, '-'
  call putchar
  dec rbx
  jmp .loop
.newline:
  mov edi, 10
  call putchar
  add rsp, 8
  pop rbx
  pop rbp
  ret

print_from_json:
  push rbp
  mov rbp, rsp
  push rbx
  push r12
  push r13
  push r14
  push r15
  sub rsp, 8

  mov r13, rdi
  mov r12, rsi

  mov rdi, r13
  lea rsi, [pat_name]
  call json_string
  test rax, rax
  jnz .have_name
  lea rax, [default_name]
.have_name:
  mov r14, rax
  lea rdi, [fmt_str]
  mov rsi, r14
  xor eax, eax
  call printf
  mov rdi, r14
  call strlen
  mov rdi, rax
  call print_dashes

  mov rdi, r13
  lea rsi, [pat_id]
  xor edx, edx
  call json_int
  lea rdi, [fmt_id]
  mov rsi, rax
  xor eax, eax
  call printf

  mov rdi, r13
  lea rsi, [pat_rating]
  xor edx, edx
  call json_int
  mov rbx, rax
  mov rdi, r13
  lea rsi, [pat_rating_highest]
  xor edx, edx
  call json_int
  mov r15, rax
  mov rax, rbx
  cqo
  mov ecx, 100
  idiv rcx
  mov r8, rax
  mov r9, rdx
  mov rax, r15
  cqo
  mov ecx, 100
  idiv rcx
  mov r10, rax
  mov r11, rdx
  lea rdi, [fmt_rating]
  mov rsi, r8
  mov rdx, r9
  mov rcx, r10
  mov r8, r11
  xor eax, eax
  call printf

  mov rdi, r13
  lea rsi, [pat_level]
  xor edx, edx
  call json_int
  lea rdi, [fmt_level]
  mov rsi, rax
  xor eax, eax
  call printf

  mov rdi, r13
  lea rsi, [pat_total]
  xor edx, edx
  call json_int
  lea rdi, [fmt_total]
  mov rsi, rax
  xor eax, eax
  call printf

  lea rdi, [fmt_str]
  lea rsi, [recent_scores]
  xor eax, eax
  call printf

  mov rdi, r12
  lea rsi, [pat_data]
  call strstr
  test rax, rax
  jz .no_data
  mov r15, rax
.find_bracket:
  mov al, [r15]
  test al, al
  jz .no_data
  cmp al, '['
  je .plays_loop_setup
  inc r15
  jmp .find_bracket

.plays_loop_setup:
  inc r15
  mov qword [loop_count], 0
  mov rax, [score_count]
  mov [score_limit], rax
.plays_loop:
  mov rax, [loop_count]
  cmp rax, [score_limit]
  jge .done
  mov rdi, r15
  call next_object
  test rax, rax
  jz .done
  mov rbx, rax
  lea r10, [rdx + 1]
  mov [restore_ptr], r10
  mov al, [r10]
  mov [restore_char], al
  mov byte [r10], 0

  mov rdi, rbx
  lea rsi, [pat_en]
  call json_string
  test rax, rax
  jnz .song_ok
  lea rax, [empty]
.song_ok:
  mov r13, rax
  mov rdi, rbx
  lea rsi, [pat_value]
  call json_string
  test rax, rax
  jnz .diff_ok
  lea rax, [empty]
.diff_ok:
  mov r14, rax
  lea rdi, [fmt_score_title]
  mov rsi, r13
  mov rdx, r14
  xor eax, eax
  call printf

  mov rdi, rbx
  lea rsi, [pat_score_fmt]
  call json_string
  test rax, rax
  jnz .score_ok
  lea rax, [empty]
.score_ok:
  mov r13, rax
  mov rdi, rbx
  lea rsi, [pat_ach_fmt]
  call json_string
  test rax, rax
  jnz .ach_ok
  lea rax, [empty]
.ach_ok:
  mov r14, rax
  mov rdi, rbx
  lea rsi, [pat_rank]
  call json_string
  test rax, rax
  jnz .rank_ok
  lea rax, [empty]
.rank_ok:
  mov [rank_value], rax
  mov rdi, rbx
  lea rsi, [pat_combo]
  call json_string
  test rax, rax
  jnz .combo_ok
  lea rax, [empty]
.combo_ok:
  lea rdi, [fmt_score_line]
  mov rsi, r13
  mov rdx, r14
  mov rcx, [rank_value]
  mov r8, rax
  xor eax, eax
  call printf

  mov r10, [restore_ptr]
  mov al, [restore_char]
  mov [r10], al
  mov r15, r10
  inc qword [loop_count]
  jmp .plays_loop

.no_data:
  lea rdi, [msg_no_data]
  call die

.done:
  add rsp, 8
  pop r15
  pop r14
  pop r13
  pop r12
  pop rbx
  pop rbp
  ret

section .note.GNU-stack noalloc noexec nowrite progbits

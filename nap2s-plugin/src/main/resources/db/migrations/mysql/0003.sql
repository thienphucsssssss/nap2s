-- điều chỉnh uuid các table cũ thành char(36)
alter table nap2s_napthe_log
    modify uuid char(36) not null;

alter table nap2s_player_data
    modify uuid char(36) not null;

alter table nap2s_point_log
    modify uuid char(36) not null;

-- them index cho uuid
create index nap2s_point_log_uuid_index
    on nap2s_point_log (uuid);

create index nap2s_napthe_log_uuid_index
    on nap2s_napthe_log (uuid);

-- tạo bảng lưu uuid
create table nap2s_player_info
(
    uuid char(36)    not null,
    name varchar(32) not null,
    last_updated bigint not null,
    constraint nap2s_player_info_pk
        primary key (uuid)
);
create index nap2s_player_info_name_index
    on nap2s_player_info (name);
